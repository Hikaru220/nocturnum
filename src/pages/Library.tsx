import { useState } from "react";
import { save } from "@tauri-apps/plugin-dialog";
import { Copy, Download, FolderOpen, MonitorUp, Plus, Trash2, Upload, Library as LibIcon } from "lucide-react";
import { api, errText, type Instance } from "../lib/api";
import { formatPlayTime, useApp } from "../lib/store";
import { Button, Card, ContextMenu, Empty, Modal, type MenuItem } from "../components/ui";
import { InlineProgress, InstanceIcon, LoaderBadge, PlayButton, pickFiles } from "../components/shared";

export default function Library() {
  const { t, instances, navigate, setCreate, refreshInstances, toast } = useApp();
  const [menu, setMenu] = useState<{ x: number; y: number; inst: Instance } | null>(null);
  const [confirmDel, setConfirmDel] = useState<Instance | null>(null);

  const guard = async (fn: () => Promise<unknown>, ok?: string) => {
    try {
      await fn();
      if (ok) toast("ok", ok);
    } catch (e) {
      toast("error", errText(e));
    }
  };

  const importPack = async () => {
    const [f] = await pickFiles([{ name: "Modpack", extensions: ["mrpack", "zip"] }]);
    if (!f) return;
    await guard(async () => {
      if (f.toLowerCase().endsWith(".mrpack")) {
        await api.importMrpack(f);
        toast("ok", t("lib.imported"));
      } else {
        const r = await api.importCurseforge(f);
        toast(r.missing ? "info" : "ok", r.missing ? t("lib.cfMissing", { n: r.missing }) : t("lib.imported"));
      }
      await refreshInstances();
    });
  };

  const items = (i: Instance): MenuItem[] => [
    { label: t("lib.menu.open"), icon: <FolderOpen className="size-4" />, onClick: () => guard(() => api.openInstanceFolder(i.id)) },
    {
      label: t("lib.menu.duplicate"),
      icon: <Copy className="size-4" />,
      onClick: () => guard(async () => { await api.duplicateInstance(i.id); await refreshInstances(); }),
    },
    {
      label: t("lib.menu.export"),
      icon: <Upload className="size-4" />,
      onClick: async () => {
        const dest = await save({ defaultPath: `${i.name}.mrpack`, filters: [{ name: "Modrinth modpack", extensions: ["mrpack"] }] });
        if (dest) guard(() => api.exportMrpack(i.id, dest), t("lib.exported"));
      },
    },
    { label: t("lib.menu.shortcut"), icon: <MonitorUp className="size-4" />, onClick: () => guard(() => api.createShortcut(i.id), t("lib.shortcutDone")) },
    { label: t("lib.menu.delete"), icon: <Trash2 className="size-4" />, danger: true, onClick: () => setConfirmDel(i) },
  ];

  return (
    <div className="anim-page p-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-extrabold">{t("lib.title")}</h1>
        <div className="flex gap-3">
          <Button icon={<Download className="size-4" />} onClick={importPack}>
            {t("lib.import")}
          </Button>
          <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setCreate(true)}>
            {t("lib.new")}
          </Button>
        </div>
      </div>

      {instances.length === 0 ? (
        <Empty icon={<LibIcon className="size-10" />} title={t("lib.empty")} hint={t("home.noInstancesHint")} />
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
          {instances.map((i) => (
            <Card
              key={i.id}
              tabIndex={0}
              role="button"
              onKeyDown={(e) => e.key === "Enter" && navigate({ page: "instance", id: i.id })}
              onClick={() => navigate({ page: "instance", id: i.id })}
              onContextMenu={(e) => {
                e.preventDefault();
                setMenu({ x: e.clientX, y: e.clientY, inst: i });
              }}
              className="p-4 cursor-pointer hover:border-accent/60 hover:-translate-y-0.5 transition-all"
            >
              <div className="flex items-center gap-4">
                <InstanceIcon inst={i} size={56} />
                <div className="min-w-0 flex-1">
                  <div className="font-bold truncate">{i.name}</div>
                  <LoaderBadge inst={i} />
                  <div className="text-xs text-faint mt-0.5">
                    {t("lib.playtime")}: {i.playTimeSecs ? formatPlayTime(i.playTimeSecs) : "—"}
                  </div>
                </div>
              </div>
              <InlineProgress id={`install:${i.id}`} />
              <div className="mt-4" onClick={(e) => e.stopPropagation()}>
                <PlayButton inst={i} className="w-full" />
              </div>
            </Card>
          ))}
        </div>
      )}

      {menu && <ContextMenu x={menu.x} y={menu.y} items={items(menu.inst)} onClose={() => setMenu(null)} />}
      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title={t("common.delete")}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDel(null)}>
              {t("common.cancel")}
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                const i = confirmDel!;
                setConfirmDel(null);
                await guard(async () => { await api.deleteInstance(i.id); await refreshInstances(); });
              }}
            >
              {t("common.delete")}
            </Button>
          </>
        }
      >
        <p>{t("lib.deleteConfirm", { name: confirmDel?.name ?? "" })}</p>
      </Modal>
    </div>
  );
}
