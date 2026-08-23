package com.planshift.datagen;

public record DataGenConfig(
        long seed,
        int numCustomers,
        int numProducts,
        int numOrders,
        int maxItemsPerOrder
) {
    public static DataGenConfig defaultSmall() {
        return new DataGenConfig(42L, 1_000, 200, 5_000, 5);
    }

    public static DataGenConfig defaultFull() {
        return new DataGenConfig(42L, 100_000, 2_000, 500_000, 5);
    }
}
