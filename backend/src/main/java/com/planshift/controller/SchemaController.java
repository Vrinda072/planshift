package com.planshift.controller;

import com.planshift.schema.SchemaService;
import com.planshift.schema.TableInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SchemaController {

    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping("/api/schema/tables")
    public List<TableInfo> listTables() {
        return schemaService.listTables();
    }
}
