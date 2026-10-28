import { Link } from "react-router-dom";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

export function NotFound() {
  useDocumentTitle("Not found");
  return (
    <PageHeader
      title="Page not found"
      sub={
        <>
          There is nothing at this address. Go back to the <Link to="/" style={{ color: "var(--accent)" }}>overview</Link> or
          search with the box above.
        </>
      }
    />
  );
}
