package com.planshift.datagen;

public record DataGenConfig(
        long seed,
        int numCustomers,
        int numProducts,
        int numOrders,
        int maxItemsPerOrder
) {
    public static DataGenConfig defaultFull() {
        return new DataGenConfig(42L, 100_000, 2_000, 500_000, 5);
    }

    public DataGenConfig withOverrides(Long seed, Integer customers, Integer products,
                                        Integer orders, Integer maxItemsPerOrder) {
        return new DataGenConfig(
                seed != null ? seed : this.seed,
                customers != null ? customers : this.numCustomers,
                products != null ? products : this.numProducts,
                orders != null ? orders : this.numOrders,
                maxItemsPerOrder != null ? maxItemsPerOrder : this.maxItemsPerOrder);
    }
}
