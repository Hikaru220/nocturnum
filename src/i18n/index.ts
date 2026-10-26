import ru, { type Key } from "./ru";
import en from "./en";

export type { Key };

const dicts: Record<string, Record<string, string>> = { ru, en };

export function translate(lang: string, key: Key, vars?: Record<string, string | number>): string {
  let s = dicts[lang]?.[key] ?? ru[key] ?? key;
  if (vars) {
    for (const [k, v] of Object.entries(vars)) s = s.split(`{${k}}`).join(String(v));
  }
  return s;
}
