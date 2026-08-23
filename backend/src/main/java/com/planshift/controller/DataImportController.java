package com.planshift.controller;

import com.planshift.dataimport.CsvImportService;
import com.planshift.dataimport.ImportResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class DataImportController {

    private final CsvImportService csvImportService;

    public DataImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping("/api/datasets/import")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file,
                                   @RequestParam("tableName") String tableName) throws IOException {
        return csvImportService.importCsv(tableName, file);
    }
}
