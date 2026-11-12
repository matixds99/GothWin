// Gothic II: New Balance - the wisp of Vatras.
// In NB the Union plugin (UNION_ABI.DLL) calls the wisp (WISP_DETECTOR) with L-Shift+F5 when the hero has the amulet
// of the wisp; the scripts have only the character and its AI (ZS_MM_Rtn_Summoned follows the hero, B_MM_WispDetect
// finds the items). Here the mod does what the plugin did.

const WISP     = "WISP_DETECTOR";
const AMULETS  = ["ITAM_ADDON_WISPDETECTORNEW", "ITAM_ADDON_WISPDETECTOR"];

let wisp  = null;                       // the character of the wisp that this mod has called
let shift = false;

function hasAmulet() {
    return AMULETS.some(a => player.valid && player.hasItem(a));
}

function findWisp() {
    if (wisp && wisp.valid && !wisp.dead)
        return wisp;
    // a wisp of a save or of the scripts: the nearest one
    const w = npcs.find(WISP);
    if (w && w.valid && !w.dead) {
        wisp = w;
        return w;
    }
    return null;
}

function spawnWisp(quiet) {
    if (!player.valid || player.dead)
        return false;
    if (!hasAmulet()) {
        if (!quiet) screen.print("Nie masz amuletu wędrującego ognika.");
        return false;
    }
    // a bit in front of the hero, at the height of the head
    const r = (player.rotation || 0) * Math.PI / 180;
    const p = player.position;
    const x = p.x + Math.sin(r) * 120;
    const z = p.z + Math.cos(r) * 120;
    const w = npcs.spawn(WISP, x, p.y + 60, z, player.rotation || 0);
    if (!w) {
        if (!quiet) screen.print("Nie udało się przywołać ognika.");
        return false;
    }
    wisp = w;
    storage.set("on", true);
    if (!quiet) screen.print("Ognik przywołany");
    return true;
}

function dismissWisp() {
    const w = findWisp();
    if (w) w.remove();
    wisp = null;
    storage.set("on", false);
    screen.print("Ognik odesłany");
}

function toggleWisp() {
    if (findWisp()) dismissWisp();
    else spawnWisp(false);
}

// --- L-Shift+F5, as in the plugin (a keyboard connected to the phone, or the computer)
events.on("key:down", ({ key }) => {
    if (key === "Shift") { shift = true; return; }
    if (key === "F5" && (shift || input.isDown("Shift")))
        toggleWisp();
});
events.on("key:up", ({ key }) => {
    if (key === "Shift") shift = false;
});

// --- the phone: a tab in the menu of the mods
ui.tab("Ognik", () => {
    const on = !!findWisp();
    ui.textWrapped("Wędrujący ognik od Vatrasa. Szuka przedmiotów i świeci w ciemności.");
    ui.spacing();
    if (!hasAmulet())
        ui.textColored("#d08040", "Potrzebny amulet wędrującego ognika.");
    ui.text(on ? "Ognik: przywołany" : "Ognik: odesłany");
    if (ui.button(on ? "Odeślij ognika" : "Przywołaj ognika", -1, ui.fontSize() * 2.5))
        toggleWisp();
    ui.spacing();
    ui.textDisabled("Klawiatura: Shift+F5. Konsola: ognik");
});

console.registerCommand("ognik", () => toggleWisp(), "ognik - przywołuje albo odsyła wędrującego ognika");

// --- after another world (or a load) the wisp comes back, as with the plugin
events.on("world:change", () => {
    wisp = null;
    if (!storage.get("on", false))
        return;
    setTimeout(() => {
        if (!findWisp()) spawnWisp(true);
    }, 2000);
});

// the wisp stays near: if it is lost far away (a teleport), it is called again next to the hero
setInterval(() => {
    const w = findWisp();
    if (!w || !player.valid) return;
    const a = w.position, b = player.position;
    const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
    if (dx * dx + dy * dy + dz * dz > 4000 * 4000) {
        w.remove();
        wisp = null;
        spawnWisp(true);
    }
}, 3000);
