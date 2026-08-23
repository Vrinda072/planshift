package com.planshift.queryplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlanParser parser = new PlanParser();

    @Test
    void parsesSimpleScanNode() throws Exception {
        String json = """
                {
                  "Node Type": "Seq Scan",
                  "Relation Name": "orders",
                  "Filter": "(customer_id = 500)",
                  "Plan Rows": 5,
                  "Actual Rows": 5,
                  "Actual Total Time": 0.565
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        PlanNode parsed = parser.parse(node);

        assertThat(parsed.nodeType()).isEqualTo("Seq Scan");
        assertThat(parsed.relationName()).isEqualTo("orders");
        assertThat(parsed.filter()).isEqualTo("(customer_id = 500)");
        assertThat(parsed.planRows()).isEqualTo(5);
        assertThat(parsed.actualRows()).isEqualTo(5);
        assertThat(parsed.actualTotalTimeMs()).isEqualTo(0.565);
        assertThat(parsed.children()).isEmpty();
    }

    @Test
    void parsesNestedChildren() throws Exception {
        String json = """
                {
                  "Node Type": "Sort",
                  "Actual Rows": 5,
                  "Plans": [
                    {
                      "Node Type": "Seq Scan",
                      "Relation Name": "orders",
                      "Actual Rows": 5
                    }
                  ]
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        PlanNode parsed = parser.parse(node);

        assertThat(parsed.nodeType()).isEqualTo("Sort");
        assertThat(parsed.children()).hasSize(1);
        assertThat(parsed.children().get(0).nodeType()).isEqualTo("Seq Scan");
        assertThat(parsed.children().get(0).relationName()).isEqualTo("orders");
    }

    @Test
    void indexScanFieldsAreCaptured() throws Exception {
        String json = """
                {
                  "Node Type": "Index Scan",
                  "Relation Name": "orders",
                  "Index Name": "idx_orders_customer_id",
                  "Index Cond": "(customer_id = 500)",
                  "Actual Rows": 5
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        PlanNode parsed = parser.parse(node);

        assertThat(parsed.indexName()).isEqualTo("idx_orders_customer_id");
        assertThat(parsed.indexCondition()).isEqualTo("(customer_id = 500)");
        assertThat(parsed.filter()).isNull();
    }
}
