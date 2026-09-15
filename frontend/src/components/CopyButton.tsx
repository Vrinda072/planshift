import { useState } from "react";

/**
 * Small "copy to clipboard" affordance for the generated/example SQL boxes.
 * Falls back silently if the Clipboard API is unavailable (e.g. insecure
 * context) rather than throwing in the user's face over a non-essential
 * convenience feature.
 */
export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      // Clipboard API unavailable -- nothing sensible to do beyond not crashing.
    }
  };

  return (
    <button type="button" className={`copy-btn${copied ? " copied" : ""}`} onClick={copy}>
      {copied ? "Copied" : "Copy"}
    </button>
  );
}
