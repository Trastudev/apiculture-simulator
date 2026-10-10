package com.apiculture.simulator.domain.workshop;

import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopState.Batch;

import org.junit.Assert;
import org.junit.Test;

public class WorkshopSchedulerTest {

    private static final long MIN = 60_000L;

    private static WorkshopState basic() {
        WorkshopState s = new WorkshopState();
        s.hexId = "h1";
        for (Machine m : Machine.values()) {
            s.levels.put(m, 1);
        }
        return s;
    }

    private static long fullRun(Format f) {
        return (120 + 30 + 45 + 240 + f.packMinutes) * MIN;
    }

    @Test
    public void starterCostFitsTheExtraStartingMoney() {
        Assert.assertTrue(WorkshopRules.starterCostB() <= 25000);
    }

    @Test
    public void batchRunsAllMachinesAndPacksJars() {
        WorkshopState s = basic();
        Batch b = WorkshopScheduler.receive(s, "Romero", 40, "Lleida", 0);
        WorkshopScheduler.chooseFormat(s, b.id, Format.JAR_500, 0);
        WorkshopScheduler.advance(s, fullRun(Format.JAR_500) - 1);
        Assert.assertEquals(1, s.batches.size());
        Assert.assertEquals(Machine.PACKER, s.batches.get(0).stage);
        WorkshopScheduler.advance(s, fullRun(Format.JAR_500));
        Assert.assertTrue(s.batches.isEmpty());
        WorkshopState.Packed p = s.packed("Romero", Format.JAR_500);
        Assert.assertNotNull(p);
        Assert.assertEquals(80, p.jars);
        Assert.assertEquals(40 * WorkshopRules.WAX_PER_HONEY_KG, s.waxKg, 1e-9);
    }

    @Test
    public void mixPacksEachJarSizeAndLeavesTheRestInBulk() {
        WorkshopState s = basic();
        Batch b = WorkshopScheduler.receive(s, "Romero", 10, "", 0);
        long atPacker = (120 + 30 + 45 + 240) * MIN;
        WorkshopScheduler.advance(s, atPacker);
        Assert.assertTrue(b.waitingFormat());
        Assert.assertTrue(WorkshopScheduler.chooseMix(s, b.id, new JarMix(3, 4, 6), atPacker));
        WorkshopScheduler.advance(s, atPacker + 1000 * MIN);
        Assert.assertTrue(s.batches.isEmpty());
        Assert.assertEquals(3, s.packed("Romero", Format.JAR_1000).jars);
        Assert.assertEquals(4, s.packed("Romero", Format.JAR_500).jars);
        Assert.assertEquals(6, s.packed("Romero", Format.JAR_250).jars);
        Assert.assertEquals(3.5, s.packed("Romero", Format.BULK).kg, 1e-9);
    }

    @Test
    public void mixCannotTakeMoreHoneyThanTheBatch() {
        WorkshopState s = basic();
        Batch b = WorkshopScheduler.receive(s, "Romero", 2, "", 0);
        Assert.assertFalse(WorkshopScheduler.chooseMix(s, b.id, new JarMix(2, 1, 0), 0));
        Assert.assertNull(b.mix);
    }

    @Test
    public void packerWaitsForFormat() {
        WorkshopState s = basic();
        Batch b = WorkshopScheduler.receive(s, "Romero", 40, "", 0);
        long atPacker = (120 + 30 + 45 + 240) * MIN;
        WorkshopScheduler.advance(s, atPacker + 600 * MIN);
        Assert.assertTrue(b.waitingFormat());
        long chosen = atPacker + 600 * MIN;
        WorkshopScheduler.chooseFormat(s, b.id, Format.BULK, chosen);
        WorkshopScheduler.advance(s, chosen + Format.BULK.packMinutes * MIN);
        Assert.assertTrue(s.batches.isEmpty());
        Assert.assertEquals(40, s.packed("Romero", Format.BULK).kg, 1e-9);
    }

    @Test
    public void singleExtractorQueuesFifoEvenWhenAdvancedLate() {
        WorkshopState s = basic();
        Batch a = WorkshopScheduler.receive(s, "Romero", 40, "", 0);
        Batch b = WorkshopScheduler.receive(s, "Brezo", 40, "", 0);
        WorkshopScheduler.advance(s, 10_000 * MIN);
        Assert.assertTrue(a.waitingFormat());
        Assert.assertTrue(b.waitingFormat());
        // A: desopercula 120-150, extrae 150-195, madura 195-435. B entra en el único depósito al salir A.
        Assert.assertEquals(435 * MIN, a.waitingSince);
        Assert.assertEquals(675 * MIN, b.waitingSince);
    }

    @Test
    public void missingMachineHoldsBatchUntilBought() {
        WorkshopState s = basic();
        s.levels.put(Machine.EXTRACTOR, 0);
        Batch b = WorkshopScheduler.receive(s, "Romero", 40, "", 0);
        WorkshopScheduler.advance(s, 1000 * MIN);
        Assert.assertEquals(Machine.EXTRACTOR, b.stage);
        Assert.assertFalse(b.inMachine);
        WorkshopScheduler.setLevel(s, Machine.EXTRACTOR, 1, 1000 * MIN);
        Assert.assertTrue(b.inMachine);
        Assert.assertEquals(1000 * MIN, b.startAt);
    }

    @Test
    public void biggerBatchTakesLongerAndLevelsSpeedUp() {
        long l1 = WorkshopRules.durationMs(Machine.EXTRACTOR, 1, 80, null);
        Assert.assertEquals(90 * MIN, l1);
        long l3 = WorkshopRules.durationMs(Machine.EXTRACTOR, 3, 80, null);
        Assert.assertTrue(l3 < l1 / 2);
        Assert.assertEquals(2, WorkshopRules.slots(Machine.MATURER, 2));
    }

    @Test
    public void jarLeftoverGoesToBulk() {
        WorkshopState s = basic();
        Batch b = WorkshopScheduler.receive(s, "Romero", 10.3, "", 0);
        WorkshopScheduler.chooseFormat(s, b.id, Format.JAR_1000, 0);
        WorkshopScheduler.advance(s, 10_000 * MIN);
        Assert.assertEquals(10, s.packed("Romero", Format.JAR_1000).jars);
        Assert.assertEquals(0.3, s.packed("Romero", Format.BULK).kg, 1e-6);
    }
}
