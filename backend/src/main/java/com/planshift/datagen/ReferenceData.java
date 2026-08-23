package com.planshift.datagen;

final class ReferenceData {

    static final String[] FIRST_NAMES = {
            "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
            "David", "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
            "Thomas", "Sarah", "Charles", "Karen", "Priya", "Arjun", "Wei", "Fatima",
            "Hiroshi", "Elena", "Carlos", "Amara", "Yuki", "Omar"
    };

    static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Wilson", "Anderson", "Taylor", "Thomas", "Moore", "Jackson",
            "Sharma", "Patel", "Chen", "Kim", "Nakamura", "Ivanov", "Silva", "Okafor",
            "Tanaka", "Rossi", "Muller", "Andersson", "Kowalski", "Haddad"
    };

    static final String[][] CITY_STATE_COUNTRY = {
            {"New York", "NY", "USA"}, {"Los Angeles", "CA", "USA"}, {"Chicago", "IL", "USA"},
            {"Houston", "TX", "USA"}, {"Phoenix", "AZ", "USA"}, {"Toronto", "ON", "Canada"},
            {"Vancouver", "BC", "Canada"}, {"London", "England", "UK"}, {"Manchester", "England", "UK"},
            {"Mumbai", "MH", "India"}, {"Bengaluru", "KA", "India"}, {"Delhi", "DL", "India"},
            {"Sydney", "NSW", "Australia"}, {"Berlin", "BE", "Germany"}, {"Paris", "IDF", "France"}
    };

    static final String[] PRODUCT_CATEGORIES = {
            "Electronics", "Home & Kitchen", "Books", "Clothing", "Sports & Outdoors",
            "Toys & Games", "Beauty", "Automotive", "Office Supplies", "Garden"
    };

    static final String[] PRODUCT_ADJECTIVES = {
            "Premium", "Compact", "Wireless", "Portable", "Classic", "Deluxe", "Eco", "Smart",
            "Heavy-Duty", "Lightweight"
    };

    static final String[] PRODUCT_NOUNS = {
            "Charger", "Backpack", "Blender", "Notebook", "Headphones", "Lamp", "Chair",
            "Water Bottle", "Keyboard", "Monitor", "Jacket", "Sneakers", "Watch", "Speaker", "Mug"
    };

    static final String[] ORDER_STATUSES = {"COMPLETED", "COMPLETED", "COMPLETED", "SHIPPED", "PENDING", "CANCELLED"};

    private ReferenceData() {
    }
}
