#include "input_cfg.h"
#include <Preferences.h>
#include <string.h>

InputCfgItem inputCfg[INPUT_COUNT];

static bool neutralSimulation = false;
static bool sensorSimulation[INPUT_COUNT] = {false};
static bool bleInDown[INPUT_COUNT] = {false};
static bool effectiveInputState[INPUT_COUNT] = {false};
static bool animOverride[INPUT_COUNT] = {false};
static bool animOverrideOn[INPUT_COUNT] = {false};

static InputCfgItem pendingCfg[INPUT_COUNT];
static bool txnActive = false;
static uint16_t stagedMask = 0;
static unsigned long txnDeadline = 0;
static char rejectReason[80] = "";

static const unsigned long TXN_MS = 1500;

bool outAssigned(uint8_t outIndex) {
    return outIndex <= 9;
}

bool isSystemFunction(uint8_t functionId) {
    switch (functionId) {
        case FN_LEFT:
        case FN_RIGHT:
        case FN_LIGHTS_1:
        case FN_BRAKE_1:
        case FN_NEUTRAL:
        case FN_STARTER:
            return true;
        default:
            return false;
    }
}

const char* inputCfgRejectReason() {
    return rejectReason;
}

static void clearSlot(InputCfgItem& item) {
    item.category = CAT_DISABLED;
    item.functionId = FN_NONE;
    item.outPrimary = OUT_NONE;
    item.outSecondary = OUT_NONE;
    item.outputEnabled = false;
    item.fixed = false;
    item.outLocked = false;
    strncpy(item.name, "DISABLED", 15);
    item.name[15] = '\0';
}

static void setSlot(InputCfgItem& item, uint8_t category, uint8_t functionId,
                    uint8_t primary, uint8_t secondary, const char* name,
                    bool outEn, bool fixed, bool outLocked) {
    item.category = category;
    item.functionId = functionId;
    item.outPrimary = primary;
    item.outSecondary = secondary;
    item.outputEnabled = outEn;
    item.fixed = fixed;
    item.outLocked = outLocked;
    strncpy(item.name, name, 15);
    item.name[15] = '\0';
}

int findFunctionInIndex(uint8_t functionId) {
    if (functionId == FN_NONE) return -1;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (inputCfg[i].functionId == functionId) return i;
    }
    return -1;
}

int findStarterInIndex() { return findFunctionInIndex(FN_STARTER); }
int findNeutralInIndex() { return findFunctionInIndex(FN_NEUTRAL); }
int findOilInIndex()     { return findFunctionInIndex(FN_OIL); }
int findFuelInIndex()    { return findFunctionInIndex(FN_FUEL); }

int starterOutIndex() {
    int si = findStarterInIndex();
    if (si < 0 || !outAssigned(inputCfg[si].outPrimary)) return -1;
    return (int)inputCfg[si].outPrimary;
}

int starterKillOutIndex() {
    int ki = findFunctionInIndex(FN_KILL_SWITCH);
    if (ki >= 0 && outAssigned(inputCfg[ki].outPrimary))
        return (int)inputCfg[ki].outPrimary;

    int si = findStarterInIndex();
    if (si < 0 || !outAssigned(inputCfg[si].outSecondary)) return -1;
    return (int)inputCfg[si].outSecondary;
}

static uint8_t canonicalCategory(uint8_t functionId, uint8_t fallback) {
    switch (functionId) {
        case FN_LEFT:
        case FN_RIGHT:
        case FN_LIGHTS_1:
        case FN_LIGHTS_2:
        case FN_STARTER:
        case FN_KILL_SWITCH:
            return CAT_BUTTON;
        case FN_BRAKE_1:
        case FN_BRAKE_2:
        case FN_NEUTRAL:
        case FN_CLUTCH:
        case FN_OIL:
        case FN_FUEL:
            return CAT_SENSOR;
        case FN_NONE:
            return CAT_DISABLED;
        case FN_USER:
            return (fallback == CAT_SENSOR) ? CAT_SENSOR : CAT_BUTTON;
        default:
            return CAT_DISABLED;
    }
}

static const char* defaultName(uint8_t functionId, uint8_t category) {
    switch (functionId) {
        case FN_LEFT:     return "LEFT BLINKER";
        case FN_RIGHT:    return "RIGHT BLINKER";
        case FN_LIGHTS_1: return "LIGHTS";
        case FN_LIGHTS_2: return "LOW BEAM";
        case FN_BRAKE_1:  return "BRAKES";
        case FN_BRAKE_2:  return "REAR BRAKE";
        case FN_NEUTRAL:  return "NEUTRAL";
        case FN_CLUTCH:   return "CLUTCH";
        case FN_STARTER:  return "STARTER";
        case FN_OIL:      return "OIL";
        case FN_FUEL:     return "FUEL";
        case FN_USER:     return (category == CAT_SENSOR) ? "SENSOR" : "BUTTON";
        case FN_KILL_SWITCH: return "KILL SWITCH";
        default:          return "DISABLED";
    }
}

static int countFn(const InputCfgItem* arr, uint8_t functionId) {
    int n = 0;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (arr[i].functionId == functionId) n++;
    }
    return n;
}

static int indexOfFn(const InputCfgItem* arr, uint8_t functionId) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (arr[i].functionId == functionId) return i;
    }
    return -1;
}

static uint8_t clampOut(uint8_t v) {
    return (v <= 9) ? v : OUT_NONE;
}

struct PairedFunctions {
    uint8_t first;
    uint8_t second;
    const char* singleFirstName;
    const char* splitFirstName;
    const char* splitSecondName;
};

static const PairedFunctions pairedFunctions[] = {
    {FN_LIGHTS_1, FN_LIGHTS_2, "LIGHTS", "HI BEAM", "LOW BEAM"},
    {FN_STARTER, FN_KILL_SWITCH, "STARTER", "STARTER", "KILL SWITCH"},
};

static void derivePairedFunctions(InputCfgItem* arr) {
    for (const PairedFunctions& pair : pairedFunctions) {
        int first = indexOfFn(arr, pair.first);
        int second = indexOfFn(arr, pair.second);
        if (first >= 0 && second >= 0) {
            if (!outAssigned(arr[second].outPrimary) &&
                outAssigned(arr[first].outSecondary)) {
                arr[second].outPrimary = arr[first].outSecondary;
                arr[second].outputEnabled = true;
            }
            arr[first].outSecondary = OUT_NONE;
            strncpy(arr[first].name, pair.splitFirstName, 15);
            strncpy(arr[second].name, pair.splitSecondName, 15);
            arr[first].name[15] = arr[second].name[15] = '\0';
        } else if (first >= 0) {
            strncpy(arr[first].name, pair.singleFirstName, 15);
            arr[first].name[15] = '\0';
        }
    }
}

static void restorePairedSecondary(InputCfgItem* arr, uint8_t oldFunction,
                                   uint8_t newFunction, uint8_t oldPrimary) {
    for (const PairedFunctions& pair : pairedFunctions) {
        if (oldFunction != pair.second || newFunction == pair.second) continue;
        int first = indexOfFn(arr, pair.first);
        if (first >= 0 && !outAssigned(arr[first].outSecondary) &&
            outAssigned(oldPrimary)) {
            arr[first].outSecondary = oldPrimary;
        }
    }
}

/** Nazwy / locki / współdzielony OUT hamulca. Bez kradzieży portów. */
static void deriveDerived(InputCfgItem* arr) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        InputCfgItem& it = arr[i];
        if (it.functionId == FN_NONE || it.category == CAT_DISABLED) {
            clearSlot(it);
            continue;
        }

        it.outPrimary = clampOut(it.outPrimary);
        it.outSecondary = clampOut(it.outSecondary);
        it.category = canonicalCategory(it.functionId, it.category);
        it.fixed = isSystemFunction(it.functionId);
        it.outLocked = false;

        if (it.functionId == FN_KILL_SWITCH) {
            it.outputEnabled = outAssigned(it.outPrimary);
            it.outSecondary = OUT_NONE;
            strncpy(it.name, defaultName(it.functionId, it.category), 15);
            it.name[15] = '\0';
            continue;
        }

        const bool sensorOptional =
            (it.functionId == FN_USER && it.category == CAT_SENSOR) ||
            it.functionId == FN_NEUTRAL || it.functionId == FN_CLUTCH ||
            it.functionId == FN_OIL || it.functionId == FN_FUEL;

        if (it.functionId == FN_USER && it.category == CAT_BUTTON) {
            it.outputEnabled = true;
        } else if (sensorOptional) {
            if (!it.outputEnabled) it.outPrimary = OUT_NONE;
            it.outputEnabled = it.outputEnabled && outAssigned(it.outPrimary);
            if (it.functionId != FN_USER) {
                strncpy(it.name, defaultName(it.functionId, it.category), 15);
                it.name[15] = '\0';
            }
        } else {
            it.outputEnabled = outAssigned(it.outPrimary);
            strncpy(it.name, defaultName(it.functionId, it.category), 15);
            it.name[15] = '\0';
            if (it.functionId != FN_LIGHTS_1 && it.functionId != FN_STARTER)
                it.outSecondary = OUT_NONE;
        }
    }

    derivePairedFunctions(arr);

    int b1 = indexOfFn(arr, FN_BRAKE_1);
    int b2 = indexOfFn(arr, FN_BRAKE_2);

    if (b1 >= 0 && b2 >= 0) {
        arr[b2].outPrimary = arr[b1].outPrimary;
        arr[b2].outSecondary = OUT_NONE;
        arr[b2].outputEnabled = arr[b1].outputEnabled;
        arr[b2].outLocked = true;
        arr[b2].category = CAT_SENSOR;
        strncpy(arr[b1].name, "FRONT BRAKE", 15);
        strncpy(arr[b2].name, "REAR BRAKE", 15);
        arr[b1].name[15] = arr[b2].name[15] = '\0';
    } else if (b1 >= 0) {
        strncpy(arr[b1].name, "BRAKES", 15);
        arr[b1].name[15] = '\0';
    }
}

static bool occupiedOut(const InputCfgItem* arr, uint8_t out, int skipA, int skipB) {
    if (!outAssigned(out)) return false;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (i == skipA || i == skipB) continue;
        if (arr[i].functionId == FN_NONE) continue;
        if (!arr[i].outputEnabled &&
            (arr[i].functionId == FN_USER || arr[i].functionId == FN_NEUTRAL ||
             arr[i].functionId == FN_CLUTCH || arr[i].functionId == FN_OIL ||
             arr[i].functionId == FN_FUEL)) continue;
        if (arr[i].outPrimary == out) return true;
        if ((arr[i].functionId == FN_LIGHTS_1 || arr[i].functionId == FN_STARTER) &&
            arr[i].outSecondary == out) return true;
    }
    return false;
}

static bool validateCfg(const InputCfgItem* arr) {
    rejectReason[0] = '\0';

    const uint8_t required[] = {
        FN_LEFT, FN_RIGHT, FN_LIGHTS_1, FN_BRAKE_1, FN_NEUTRAL, FN_STARTER
    };
    for (uint8_t fn : required) {
        int n = countFn(arr, fn);
        if (n != 1) {
            snprintf(rejectReason, sizeof(rejectReason),
                     "missing/dup fn %u (%d)", fn, n);
            return false;
        }
    }

    const uint8_t atMostOne[] = {
        FN_LIGHTS_2, FN_BRAKE_2, FN_CLUTCH, FN_OIL, FN_FUEL, FN_KILL_SWITCH
    };
    for (uint8_t fn : atMostOne) {
        if (countFn(arr, fn) > 1) {
            snprintf(rejectReason, sizeof(rejectReason), "dup fn %u", fn);
            return false;
        }
    }

    for (int i = 0; i < INPUT_COUNT; i++) {
        const InputCfgItem& it = arr[i];
        if (it.functionId == FN_NONE) continue;

        if (isSystemFunction(it.functionId) && it.category == CAT_DISABLED) {
            snprintf(rejectReason, sizeof(rejectReason), "system fn disabled");
            return false;
        }

        const bool needsOut =
            it.functionId == FN_LEFT || it.functionId == FN_RIGHT ||
            it.functionId == FN_STARTER || it.functionId == FN_LIGHTS_1 ||
            it.functionId == FN_LIGHTS_2 || it.functionId == FN_BRAKE_1 ||
            it.functionId == FN_BRAKE_2 || it.functionId == FN_KILL_SWITCH;
        if (needsOut && !outAssigned(it.outPrimary)) {
            snprintf(rejectReason, sizeof(rejectReason),
                     "fn %u needs OUT", it.functionId);
            return false;
        }
        const bool sensorOptional =
            (it.functionId == FN_USER && it.category == CAT_SENSOR) ||
            it.functionId == FN_NEUTRAL || it.functionId == FN_CLUTCH ||
            it.functionId == FN_OIL || it.functionId == FN_FUEL;
        if (it.functionId == FN_USER && it.category == CAT_BUTTON &&
            !outAssigned(it.outPrimary)) {
            snprintf(rejectReason, sizeof(rejectReason), "USER BUTTON needs OUT");
            return false;
        }
        if (it.functionId == FN_KILL_SWITCH && !outAssigned(it.outPrimary)) {
            snprintf(rejectReason, sizeof(rejectReason), "KILL SWITCH needs OUT");
            return false;
        }
        if (sensorOptional && it.outputEnabled && !outAssigned(it.outPrimary)) {
            snprintf(rejectReason, sizeof(rejectReason), "optional OUT missing");
            return false;
        }
        if (outAssigned(it.outPrimary) && outAssigned(it.outSecondary) &&
            it.outPrimary == it.outSecondary) {
            snprintf(rejectReason, sizeof(rejectReason), "HI=LOW");
            return false;
        }
    }

    int l1 = indexOfFn(arr, FN_LIGHTS_1);
    int l2 = indexOfFn(arr, FN_LIGHTS_2);
    if (l1 >= 0 && l2 < 0 && !outAssigned(arr[l1].outSecondary)) {
        snprintf(rejectReason, sizeof(rejectReason), "LIGHTS needs LOW OUT");
        return false;
    }

    int b1 = indexOfFn(arr, FN_BRAKE_1);
    int b2 = indexOfFn(arr, FN_BRAKE_2);

    for (int i = 0; i < INPUT_COUNT; i++) {
        const InputCfgItem& it = arr[i];
        if (it.functionId == FN_NONE) continue;
        if (it.functionId == FN_BRAKE_2) continue;

        if (outAssigned(it.outPrimary) &&
            occupiedOut(arr, it.outPrimary, i, (i == b1) ? b2 : -1)) {
            snprintf(rejectReason, sizeof(rejectReason),
                     "OUT %u conflict", it.outPrimary + 1);
            return false;
        }
        if ((it.functionId == FN_LIGHTS_1 || it.functionId == FN_STARTER) &&
            outAssigned(it.outSecondary) &&
            occupiedOut(arr, it.outSecondary, i, -1)) {
            snprintf(rejectReason, sizeof(rejectReason),
                     "OUT %u conflict", it.outSecondary + 1);
            return false;
        }
    }
    return true;
}

static void dedupe(InputCfgItem* arr) {
    bool seen[FN_KILL_SWITCH + 1] = {false};
    for (int i = 0; i < INPUT_COUNT; i++) {
        uint8_t fn = arr[i].functionId;
        if (fn == FN_NONE || fn == FN_USER) continue;
        if (fn > FN_KILL_SWITCH) {
            clearSlot(arr[i]);
            continue;
        }
        if (seen[fn]) clearSlot(arr[i]);
        else seen[fn] = true;
    }
}

static int firstDisabled(const InputCfgItem* arr) {
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (arr[i].functionId == FN_NONE) return i;
    }
    return -1;
}

static uint8_t firstFreeOut(const InputCfgItem* arr, uint8_t alsoSkip) {
    for (uint8_t o = 0; o < 10; o++) {
        if (o == alsoSkip) continue;
        if (!occupiedOut(arr, o, -1, -1)) return o;
    }
    return OUT_NONE;
}

static void ensureSystem(InputCfgItem* arr) {
    struct Def {
        uint8_t fn;
        uint8_t pri;
        uint8_t sec;
        const char* name;
    };
    const Def defs[] = {
        {FN_LEFT,     0, OUT_NONE, "LEFT BLINKER"},
        {FN_RIGHT,    1, OUT_NONE, "RIGHT BLINKER"},
        {FN_BRAKE_1,  2, OUT_NONE, "BRAKES"},
        {FN_STARTER,  3, 7, "STARTER"},
        {FN_NEUTRAL,  4, OUT_NONE, "NEUTRAL"},
        {FN_LIGHTS_1, 5, 6,        "LIGHTS"},
    };
    for (const Def& d : defs) {
        if (indexOfFn(arr, d.fn) >= 0) continue;
        int slot = firstDisabled(arr);
        if (slot < 0) return;
        uint8_t pri = d.pri;
        uint8_t sec = d.sec;
        if (occupiedOut(arr, pri, -1, -1)) pri = firstFreeOut(arr, sec);
        if (d.fn == FN_LIGHTS_1) {
            if (!outAssigned(sec) || occupiedOut(arr, sec, -1, -1) || sec == pri)
                sec = firstFreeOut(arr, pri);
        }
        setSlot(arr[slot], canonicalCategory(d.fn, CAT_BUTTON), d.fn,
                pri, sec, d.name, outAssigned(pri), true, false);
    }
}

static void setDefaults(InputCfgItem* arr) {
    for (int i = 0; i < INPUT_COUNT; i++) clearSlot(arr[i]);
    setSlot(arr[0], CAT_BUTTON, FN_LEFT,     0, OUT_NONE, "LEFT BLINKER", true, true, false);
    setSlot(arr[1], CAT_BUTTON, FN_RIGHT,    1, OUT_NONE, "RIGHT BLINKER", true, true, false);
    setSlot(arr[2], CAT_SENSOR, FN_BRAKE_1,  2, OUT_NONE, "BRAKES",       true, true, false);
    setSlot(arr[3], CAT_BUTTON, FN_STARTER,  3, 7,        "STARTER",      true, true, false);
    setSlot(arr[4], CAT_SENSOR, FN_NEUTRAL,  4, OUT_NONE, "NEUTRAL",      true, true, false);
    setSlot(arr[5], CAT_BUTTON, FN_LIGHTS_1, 5, 6,        "LIGHTS",       true, true, false);
    deriveDerived(arr);
}

static void cancelTxn() {
    txnActive = false;
    stagedMask = 0;
}

static InCfgApply applyPending() {
    if (!txnActive) return INCFG_IDLE;

    InputCfgItem tmp[INPUT_COUNT];
    memcpy(tmp, pendingCfg, sizeof(tmp));
    dedupe(tmp);
    ensureSystem(tmp);
    deriveDerived(tmp);

    if (!validateCfg(tmp)) {
        cancelTxn();
        Serial.printf("INCFG REJECT: %s\n", rejectReason);
        return INCFG_REJECTED;
    }

    memcpy(inputCfg, tmp, sizeof(inputCfg));
    saveInputModes();
    cancelTxn();
    Serial.println("INCFG COMMIT OK");
    return INCFG_COMMITTED;
}

static void beginTxnIfNeeded() {
    if (txnActive) return;
    memcpy(pendingCfg, inputCfg, sizeof(pendingCfg));
    stagedMask = 0;
    txnActive = true;
    txnDeadline = millis() + TXN_MS;
}

InCfgApply stageInputCfgV4(int inIndex, uint8_t category, uint8_t functionId,
                           uint8_t outPrimary, uint8_t outSecondary,
                           bool outputEnabled, uint8_t outputIndex, const char* name) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return INCFG_REJECTED;
    if (category > CAT_DISABLED || functionId > FN_KILL_SWITCH) return INCFG_REJECTED;

    beginTxnIfNeeded();
    txnDeadline = millis() + TXN_MS;

    uint8_t pri = clampOut(outPrimary);
    uint8_t sec = clampOut(outSecondary);
    uint8_t oix = clampOut(outputIndex);
    if (functionId == FN_USER) {
        if (category == CAT_BUTTON) {
            if (outAssigned(oix)) pri = oix;
            outputEnabled = true;
        } else {
            if (outputEnabled && outAssigned(oix)) pri = oix;
            if (!outputEnabled) pri = OUT_NONE;
        }
    }
    if ((functionId == FN_NEUTRAL || functionId == FN_CLUTCH ||
         functionId == FN_OIL || functionId == FN_FUEL) && !outputEnabled) {
        pri = OUT_NONE;
    }

    InputCfgItem& it = pendingCfg[inIndex];
    restorePairedSecondary(pendingCfg, it.functionId, functionId, it.outPrimary);
    it.category = category;
    it.functionId = functionId;
    it.outPrimary = pri;
    it.outSecondary = sec;
    it.outputEnabled = outputEnabled;
    it.fixed = isSystemFunction(functionId);
    it.outLocked = false;
    if (functionId == FN_USER) {
        if (name && name[0]) {
            strncpy(it.name, name, 15);
            it.name[15] = '\0';
        } else {
            it.name[0] = '\0';
        }
    } else if (name && name[0]) {
        strncpy(it.name, name, 15);
        it.name[15] = '\0';
    } else {
        strncpy(it.name, defaultName(functionId, category), 15);
        it.name[15] = '\0';
    }

    stagedMask |= (uint16_t)(1u << inIndex);
    if (stagedMask == 0x03FF) return applyPending();
    return INCFG_STAGED;
}

InCfgApply commitInputCfg() {
    if (!txnActive) return INCFG_IDLE;
    return applyPending();
}

InCfgApply pollInputCfgTxn() {
    if (!txnActive) return INCFG_IDLE;
    if ((long)(millis() - txnDeadline) < 0) return INCFG_IDLE;
    return applyPending();
}

bool setInputCfg(int inIndex, uint8_t mode, uint8_t outIndex,
                 bool outputEnabled, uint8_t outputIndex, const char* name) {
    InCfgApply r = stageInputCfgV4(inIndex, mode, FN_USER, outIndex, OUT_NONE,
                                   outputEnabled, outputIndex, name);
    if (r == INCFG_STAGED) r = commitInputCfg();
    return r == INCFG_COMMITTED;
}

void saveInputModes() {
    Preferences p;
    if (!p.begin("yh_in", false)) return;
    p.putBool("ok", true);
    p.putBool("v4", true);
    for (int i = 0; i < INPUT_COUNT; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i); p.putUChar(k, inputCfg[i].category);
        snprintf(k, sizeof(k), "f%d", i); p.putUChar(k, inputCfg[i].functionId);
        snprintf(k, sizeof(k), "p%d", i); p.putUChar(k, inputCfg[i].outPrimary);
        snprintf(k, sizeof(k), "q%d", i); p.putUChar(k, inputCfg[i].outSecondary);
        snprintf(k, sizeof(k), "e%d", i); p.putBool(k, inputCfg[i].outputEnabled);
        snprintf(k, sizeof(k), "n%d", i); p.putString(k, inputCfg[i].name);
    }
    p.end();
}

void loadInputModes() {
    setDefaults(inputCfg);

    Preferences p;
    if (!p.begin("yh_in", false)) {
        Serial.println("INCFG: NVS open fail -> defaults");
        return;
    }

    if (!p.isKey("v4")) {
        p.end();
        saveInputModes();
        Serial.println("INCFG: Forced v4 defaults");
        return;
    }

    for (int i = 0; i < INPUT_COUNT; i++) {
        char k[8];
        snprintf(k, sizeof(k), "m%d", i);
        inputCfg[i].category = p.getUChar(k, inputCfg[i].category);

        snprintf(k, sizeof(k), "f%d", i);
        inputCfg[i].functionId = p.getUChar(k, inputCfg[i].functionId);

        snprintf(k, sizeof(k), "p%d", i);
        inputCfg[i].outPrimary = p.getUChar(k, inputCfg[i].outPrimary);

        snprintf(k, sizeof(k), "q%d", i);
        inputCfg[i].outSecondary = p.getUChar(k, inputCfg[i].outSecondary);

        snprintf(k, sizeof(k), "e%d", i);
        inputCfg[i].outputEnabled = p.getBool(k, inputCfg[i].outputEnabled);

        snprintf(k, sizeof(k), "n%d", i);
        String s = p.getString(k, inputCfg[i].name);
        strncpy(inputCfg[i].name, s.c_str(), 15);
        inputCfg[i].name[15] = '\0';
    }
    p.end();

    dedupe(inputCfg);
    ensureSystem(inputCfg);
    deriveDerived(inputCfg);
    if (!validateCfg(inputCfg)) {
        Serial.printf("INCFG: NVS invalid (%s) -> defaults\n", rejectReason);
        setDefaults(inputCfg);
        saveInputModes();
        return;
    }
    Serial.println("INCFG: loaded (V4)");
}

void setNeutralSimulation(bool on) { neutralSimulation = on; }
bool isNeutralSimulation() { return neutralSimulation; }

void setSensorSimulation(int inIndex, bool on) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    sensorSimulation[inIndex] = on;
    bleInDown[inIndex] = on;
    effectiveInputState[inIndex] = on;
}

bool isInputActive(int inIndex, bool physicalPressed) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    if (animOverride[inIndex]) {
        effectiveInputState[inIndex] = animOverrideOn[inIndex];
        return animOverrideOn[inIndex];
    }
    effectiveInputState[inIndex] = sensorSimulation[inIndex]
        ? bleInDown[inIndex]
        : (physicalPressed || bleInDown[inIndex]);
    return effectiveInputState[inIndex];
}

bool getEffectiveInputState(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    return effectiveInputState[inIndex];
}

void updateAllEffectiveInputStates(Button* buttons) {
    if (!buttons) return;
    for (int i = 0; i < INPUT_COUNT; i++) {
        if (animOverride[i]) {
            effectiveInputState[i] = animOverrideOn[i];
            continue;
        }
        bool active = sensorSimulation[i]
            ? bleInDown[i]
            : (buttons[i].isPressed() || bleInDown[i]);
        effectiveInputState[i] = active;
    }
}

void setAnimInputOverride(int inIndex, bool on) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    animOverride[inIndex] = true;
    animOverrideOn[inIndex] = on;
    effectiveInputState[inIndex] = on;
}

void clearAnimInputOverride(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    animOverride[inIndex] = false;
}

void clearAllAnimInputOverrides() {
    for (int i = 0; i < INPUT_COUNT; i++) animOverride[i] = false;
}

void setBleInputPressed(int inIndex, bool pressed) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return;
    bleInDown[inIndex] = pressed;
}

bool isBleInputPressed(int inIndex) {
    if (inIndex < 0 || inIndex >= INPUT_COUNT) return false;
    return bleInDown[inIndex];
}
