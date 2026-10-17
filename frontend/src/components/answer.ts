import DOMPurify from "dompurify";
import { marked } from "marked";
export function renderAnswer(text: string): string {
  return DOMPurify.sanitize(marked.parse(text, { async: false }) as string, {
    FORBID_TAGS: ["img", "style", "iframe"],
    FORBID_ATTR: ["style"],
  });
}
export function safeSourceUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}
