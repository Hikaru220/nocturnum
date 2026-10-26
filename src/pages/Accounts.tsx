import { useState } from "react";
import { Check, LogIn, ShieldCheck, Trash2, UserPlus } from "lucide-react";
import { api, errText } from "../lib/api";
import { useApp } from "../lib/store";
import { Badge, Button, Card, Empty, IconButton, Input, Modal } from "../components/ui";
import { Avatar, DeviceCodeModal, useMicrosoftLogin } from "../components/shared";

export default function Accounts() {
  const { t, accounts, settings, patchSettings, refreshAccounts, toast } = useApp();
  const ms = useMicrosoftLogin();
  const [nick, setNick] = useState("");
  const [offlineOpen, setOffline] = useState(false);
  const [removeId, setRemoveId] = useState<string | null>(null);
  const cur = useApp((s) => s.currentAccount());

  const addOffline = async () => {
    try {
      const a = await api.addOffline(nick);
      await refreshAccounts();
      await patchSettings({ lastAccount: a.id });
      toast("ok", t("acc.added"));
      setOffline(false);
      setNick("");
    } catch (e) {
      toast("error", errText(e));
    }
  };
  const hasMs = accounts.some((a) => a.kind === "microsoft");
  const target = accounts.find((a) => a.id === removeId);

  return (
    <div className="anim-page p-8 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-extrabold">{t("acc.title")}</h1>
        <div className="flex gap-3">
          <Button icon={<UserPlus className="size-4" />} disabled={!hasMs} title={hasMs ? undefined : t("acc.offlineHint")} onClick={() => setOffline(true)}>
            {t("acc.addOffline")}
          </Button>
          <Button variant="primary" icon={<LogIn className="size-4" />} loading={ms.busy} onClick={ms.start}>
            {t("acc.addMicrosoft")}
          </Button>
        </div>
      </div>
      {accounts.length === 0 ? (
        <Empty icon={<UserPlus className="size-10" />} title={t("acc.empty")} />
      ) : (
        <div className="space-y-3">
          {accounts.map((a) => (
            <Card key={a.id} className={`p-4 flex items-center gap-4 ${cur?.id === a.id ? "border-accent" : ""}`}>
              <Avatar account={a} size={48} />
              <div className="flex-1 min-w-0">
                <div className="font-bold text-base truncate">{a.username}</div>
                <Badge tone={a.kind === "microsoft" ? "ok" : "neutral"}>{a.kind === "microsoft" ? t("acc.licensed") : t("acc.offline")}</Badge>
              </div>
              {cur?.id === a.id ? (
                <Badge tone="accent">
                  <Check className="size-3 mr-1" />
                  {t("acc.current")}
                </Badge>
              ) : (
                <Button onClick={() => patchSettings({ lastAccount: a.id })}>{t("acc.select")}</Button>
              )}
              <IconButton label={t("common.remove")} onClick={() => setRemoveId(a.id)}>
                <Trash2 className="size-4" />
              </IconButton>
            </Card>
          ))}
        </div>
      )}
      <p className="flex items-center gap-2 text-xs text-faint mt-6">
        <ShieldCheck className="size-4" /> {t("acc.tokensNote")}
      </p>
      {!hasMs && <p className="text-xs text-faint mt-1">{t("acc.offlineHint")}</p>}

      <DeviceCodeModal onCancel={ms.cancel} />
      <Modal
        open={offlineOpen}
        onClose={() => setOffline(false)}
        title={t("acc.addOffline")}
        width={440}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOffline(false)}>{t("common.cancel")}</Button>
            <Button variant="primary" disabled={nick.length < 3} onClick={addOffline}>{t("common.add")}</Button>
          </>
        }
      >
        <p className="text-dim mb-3">{t("acc.offlineHint")}</p>
        <Input value={nick} maxLength={16} placeholder={t("acc.nick")} onChange={(e) => setNick(e.target.value)} onKeyDown={(e) => e.key === "Enter" && nick.length >= 3 && addOffline()} />
      </Modal>
      <Modal
        open={!!target}
        onClose={() => setRemoveId(null)}
        title={t("common.remove")}
        width={420}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRemoveId(null)}>{t("common.cancel")}</Button>
            <Button
              variant="danger"
              onClick={async () => {
                await api.removeAccount(removeId!).catch((e) => toast("error", errText(e)));
                setRemoveId(null);
                await refreshAccounts();
                if (settings?.lastAccount === removeId) patchSettings({ lastAccount: "" });
              }}
            >
              {t("common.remove")}
            </Button>
          </>
        }
      >
        {t("acc.removeConfirm", { name: target?.username ?? "" })}
      </Modal>
    </div>
  );
}
