package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;

import org.junit.Assert;
import org.junit.Test;

public class WarehouseRulesTest {

    @Test
    public void sellRefund_isHalfRoundedDown() {
        Assert.assertEquals(150, WarehouseRules.sellRefundB(300));
        Assert.assertEquals(0, WarehouseRules.sellRefundB(1));
        Assert.assertEquals(50, WarehouseRules.sellRefundB(101));
    }

    @Test
    public void warehouseSellRefund_includesUpgrades() {
        Assert.assertEquals(150, WarehouseRules.warehouseSellRefundB(1));
        Assert.assertEquals(275, WarehouseRules.warehouseSellRefundB(2));
        Assert.assertEquals(525, WarehouseRules.warehouseSellRefundB(3));
    }

    @Test
    public void warehouseOnlyRow_isNotAnApiary() {
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hasWarehouse = true;
        row.warehouseLat = 41.3;
        row.warehouseLng = 2.1;
        Assert.assertFalse(WarehouseRules.isApiarySite(row));
        row.siteLat = 41.3;
        row.siteLng = 2.1;
        Assert.assertFalse(WarehouseRules.isApiarySite(row));
        row.siteLat = 41.31;
        Assert.assertTrue(WarehouseRules.isApiarySite(row));
    }
}
