package com.planshift.controller;

import com.planshift.workload.WorkloadCatalog;
import com.planshift.workload.WorkloadQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class QueryController {

    @GetMapping("/api/queries")
    public List<WorkloadQuery> listQueries() {
        return WorkloadCatalog.all();
    }
}
