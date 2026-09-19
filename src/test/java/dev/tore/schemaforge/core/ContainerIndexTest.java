package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-01: learn, look up sources, age entries and survive a save/load round trip. */
class ContainerIndexTest {
    private static final BlockPos NEAR = new BlockPos(5, 64, 0);
    private static final BlockPos FAR = new BlockPos(100, 64, 0);
    private static final Vec3 PLAYER = new Vec3(0, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void learnRecordsContentsAndTheTimeItWasSeen() {
        AtomicLong clock = new AtomicLong(1_000);
        ContainerIndex index = new ContainerIndex(clock::get);
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 64));

        ContainerIndex.Entry entry = index.at(NEAR).orElseThrow();
        assertEquals(ContainerType.CHEST, entry.type());
        assertEquals(1_000, entry.lastSeenEpochMs());
        assertEquals(64, entry.count(Items.STONE));
        assertEquals(0, entry.count(Items.TORCH));
        assertFalse(entry.stale());
    }

    @Test
    void learningAgainReplacesTheContentsAndClearsStale() {
        AtomicLong clock = new AtomicLong(1_000);
        ContainerIndex index = new ContainerIndex(clock::get);
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 64));

        clock.set(100_000);
        index.markStale(Duration.ofSeconds(10));
        assertTrue(index.at(NEAR).orElseThrow().stale());

        index.learn(NEAR, ContainerType.BARREL, Map.of(Items.TORCH, 5));
        ContainerIndex.Entry entry = index.at(NEAR).orElseThrow();
        assertFalse(entry.stale(), "a fresh look is not stale");
        assertEquals(ContainerType.BARREL, entry.type());
        assertEquals(0, entry.count(Items.STONE), "the old contents are gone, not merged");
        assertEquals(5, entry.count(Items.TORCH));
        assertEquals(1, index.size());
    }

    @Test
    void sourcesForIsSortedByDistanceWithStaleOnesLast() {
        AtomicLong clock = new AtomicLong(1_000);
        ContainerIndex index = new ContainerIndex(clock::get);
        index.learn(FAR, ContainerType.CHEST, Map.of(Items.STONE, 10));
        index.learn(NEAR, ContainerType.BARREL, Map.of(Items.STONE, 10));
        index.learn(new BlockPos(0, 64, 2), ContainerType.CHEST, Map.of(Items.TORCH, 3));

        List<BlockPos> stone = index.sourcesFor(Items.STONE, PLAYER).stream().map(ContainerIndex.Entry::pos).toList();
        assertEquals(List.of(NEAR, FAR), stone, "nearest first, and only containers holding stone");

        // The near barrel goes stale, so the far chest is tried first even though it is further away.
        clock.set(100_000);
        index.markStale(Duration.ofSeconds(10));
        index.learn(FAR, ContainerType.CHEST, Map.of(Items.STONE, 10));
        List<BlockPos> afterAging = index.sourcesFor(Items.STONE, PLAYER).stream().map(ContainerIndex.Entry::pos).toList();
        assertEquals(List.of(FAR, NEAR), afterAging);
    }

    @Test
    void markStaleOnlyTouchesOldEntries() {
        AtomicLong clock = new AtomicLong(0);
        ContainerIndex index = new ContainerIndex(clock::get);
        index.learn(FAR, ContainerType.CHEST, Map.of(Items.STONE, 1));
        clock.set(60_000);
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 1));

        clock.set(90_000);
        index.markStale(Duration.ofSeconds(60));

        assertTrue(index.at(FAR).orElseThrow().stale(), "seen 90 s ago");
        assertFalse(index.at(NEAR).orElseThrow().stale(), "seen 30 s ago");
    }

    @Test
    void forgetRemovesOneContainer() {
        ContainerIndex index = new ContainerIndex();
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 1));

        assertTrue(index.forget(NEAR));
        assertFalse(index.forget(NEAR), "already gone");
        assertEquals(0, index.size());
    }

    @Test
    void saveAndLoadKeepEverything(@TempDir Path dir) {
        AtomicLong clock = new AtomicLong(1_700_000_000_000L);
        ContainerIndex index = new ContainerIndex(clock::get);
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 64, Items.TORCH, 12));
        index.learn(FAR, ContainerType.ENDER_CHEST, Map.of(Items.OAK_PLANKS, 3));
        clock.set(clock.get() + 600_000);
        index.markStale(Duration.ofMinutes(5));

        Path file = dir.resolve("sub").resolve("containers-abc-overworld.json");
        index.save(file);
        ContainerIndex loaded = ContainerIndex.load(file, clock::get);

        assertEquals(index.entries(), loaded.entries(), "same entries, same order");
        assertTrue(loaded.at(NEAR).orElseThrow().stale());
        assertEquals(64, loaded.at(NEAR).orElseThrow().count(Items.STONE));
        assertEquals(ContainerType.ENDER_CHEST, loaded.at(FAR).orElseThrow().type());
    }

    @Test
    void theFileStartsWithTheSchemaVersionAndUsesItemIds(@TempDir Path dir) throws Exception {
        ContainerIndex index = new ContainerIndex(() -> 1_700_000_000_000L);
        index.learn(NEAR, ContainerType.CHEST, Map.of(Items.STONE, 64));

        Path file = dir.resolve("containers-abc-overworld.json");
        index.save(file);

        String json = Files.readString(file);
        assertTrue(json.replaceAll("\\s", "").startsWith("{\"v\":1,"), json);
        assertTrue(json.contains("\"minecraft:stone\": 64"), json);
        assertTrue(json.contains("\"type\": \"CHEST\""), json);
        assertTrue(json.replaceAll("\\s", "").contains("\"pos\":[5,64,0]"), json);
    }

    @Test
    void aMissingBrokenOrForeignFileLoadsAsEmpty(@TempDir Path dir) throws Exception {
        assertEquals(0, ContainerIndex.load(dir.resolve("nope.json")).size());

        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{not json");
        assertEquals(0, ContainerIndex.load(broken).size());

        Path future = dir.resolve("future.json");
        Files.writeString(future, "{\"v\":2,\"containers\":[]}");
        assertEquals(0, ContainerIndex.load(future).size());
    }

    @Test
    void unknownItemsAndTypesAreSkippedWithoutLosingTheRest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("containers.json");
        Files.writeString(file, """
            {"v":1,"containers":[
              {"pos":[5,64,0],"type":"CHEST","lastSeen":1,"stale":false,
               "items":{"minecraft:stone":64,"modded:unobtainium":7}},
              {"pos":[9,64,0],"type":"CRATE","lastSeen":1,"stale":false,"items":{"minecraft:torch":1}}
            ]}""");

        ContainerIndex index = ContainerIndex.load(file);

        assertEquals(1, index.size(), "the container with the unknown type is dropped");
        ContainerIndex.Entry entry = index.at(NEAR).orElseThrow();
        assertEquals(64, entry.count(Items.STONE));
        assertEquals(1, entry.items().size(), "the unknown item is dropped, the known one stays");
    }
}
