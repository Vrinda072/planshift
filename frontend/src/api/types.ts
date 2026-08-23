export type RegressionStatus = "IMPROVED" | "UNCHANGED" | "REGRESSED";
export type ExperimentStatus = "RUNNING" | "COMPLETED" | "FAILED";
export type ExperimentPhase =
  | "PENDING"
  | "DROPPING_INDEX"
  | "WARMING_UP_BASELINE"
  | "MEASURING_BASELINE"
  | "ADDING_INDEX"
  | "WARMING_UP_CANDIDATE"
  | "MEASURING_CANDIDATE"
  | "COMPARING_RESULTS"
  | "DONE";

export interface WorkloadQuery {
  id: string;
  name: string;
  category: string;
  description: string;
  sql: string;
}

export interface PlanNode {
  nodeType: string;
  relationName: string | null;
  indexName: string | null;
  filter: string | null;
  indexCondition: string | null;
  planRows: number;
  actualRows: number;
  actualTotalTimeMs: number;
  children: PlanNode[];
}

export interface ExperimentQueryResult {
  id: number;
  experimentId: number;
  queryId: string;
  queryName: string;
  baselineMedianMs: number;
  candidateMedianMs: number;
  absoluteChangeMs: number;
  percentageChange: number;
  status: RegressionStatus;
  baselinePlanJson: string;
  candidatePlanJson: string;
  planDiffSummary: string;
}

export interface Experiment {
  experimentId: number;
  createdAt: string;
  experimentType: string;
  datasetCustomers: number;
  datasetOrders: number;
  datasetSeed: number;
  targetTable: string | null;
  targetColumn: string | null;
  repetitions: number;
  thresholdFraction: number;
  status: ExperimentStatus;
  currentPhase: ExperimentPhase | null;
  overallPercentageChange: number | null;
  queryResults: ExperimentQueryResult[];
}

export interface ColumnInfo {
  columnName: string;
  dataType: string;
  nullable: boolean;
}

export interface TableInfo {
  tableName: string;
  columns: ColumnInfo[];
}

export interface QuerySpec {
  table: string;
  selectColumns: string[] | null;
  aggregateFunction: string | null;
  aggregateColumn: string | null;
  groupByColumn: string | null;
  filterColumn: string | null;
  filterOperator: string | null;
  filterValue: string | null;
  orderByColumn: string | null;
  orderByDirection: string | null;
  limit: number | null;
}
