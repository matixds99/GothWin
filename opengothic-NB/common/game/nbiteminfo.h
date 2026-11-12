#pragma once
// New Balance: the description of an item in the inventory (weapons, armors, jewelry).
//
// In NB a lot of the items set in the script only TEXT[5] (the value) - the rest of the
// description is made by the Union plugin (UNION_ABI.DLL) from the fields of the item,
// with the texts of the script constants RX_INV_DESC*. This is a port of that code:
//   weapon (ITEM_KAT_NF): 0 damage, 1 length | type, 2 requirements, 5 value
//   ranged (ITEM_KAT_FF): 0 damage, 1 type,          2 requirements, 5 value
//   armor  (ITEM_KAT_ARMOR): 0 edge, 1 blunt, 2 point, 3 fire | magic, 5 requirements
//   ring/amulet (ITEM_KAT_MAGIC): 0 bonuses (when the item has them)
//   + row 4: the weight (weapons, shields, armors, helmets), from RX_SetWeightValueExt / WEIGHT
// The type of a weapon gets the marks of NB from the bits in C_ITEM.HP (SetItemVarTrue):
// orc weapon (lizard for the Dracon swords), cursed, magic, sharpened.
// A shield (ITEM_KAT_NF + ITEM_SHIELD) keeps its texts from the script, as in the plugin.
// Without the constants (not NB) nothing is done.

#include <zenkit/DaedalusVm.hh>
#include <zenkit/addon/daedalus.hh>
#include <string>
#include <array>
#include <unordered_map>

namespace NbItemInfo {

// RX_SetWeightValueExt(item, index, value): the "weights" of an item instance, RX_W_* (0..9).
// They are set in the instance of the item (ItMw_..), so they are kept per the instance symbol.
constexpr size_t WeightCount = 10;
inline std::unordered_map<uint32_t, std::array<int32_t,WeightCount>>& weights() {
  static std::unordered_map<uint32_t, std::array<int32_t,WeightCount>> w;
  return w;
  }

inline void setWeight(const zenkit::DaedalusInstance& it, int32_t index, int32_t value) {
  if(index<0 || size_t(index)>=WeightCount)
    return;
  auto& w = weights()[it.symbol_index()];
  w[size_t(index)] = value;
  }

inline int32_t weight(uint32_t instance, int32_t index) {
  if(index<0 || size_t(index)>=WeightCount)
    return 0;
  auto i = weights().find(instance);
  return i==weights().end() ? 0 : i->second[size_t(index)];
  }

inline const std::string& str(zenkit::DaedalusVm& vm, const char* name) {
  static const std::string empty;
  auto* s = vm.find_symbol_by_name(name);
  if(s==nullptr || s->type()!=zenkit::DaedalusDataType::STRING)
    return empty;
  return s->get_string();
  }

inline bool isNb(zenkit::DaedalusVm& vm) {
  auto* s = vm.find_symbol_by_name("RX_INV_DESCWEAPON_DAM_MAIN");
  return s!=nullptr && s->type()==zenkit::DaedalusDataType::STRING;
  }

inline bool has(const zenkit::IItem& it, uint32_t f) {
  return (uint32_t(it.flags)&f)==f;
  }

// "Obrażenia cięte: 15, obuchowe: 5"
inline std::string damage(zenkit::DaedalusVm& vm, const zenkit::IItem& it) {
  int32_t dmg[zenkit::DamageType::NUM] = {};
  int32_t sum = 0;
  for(size_t i=0; i<zenkit::DamageType::NUM; ++i) {
    dmg[i] = it.damage[i];
    sum   += dmg[i];
    }
  if(sum==0 && it.damage_total>0) {
    // as the engine does: DAMAGETOTAL is shared out over the types of DAMAGETYPE
    int n = 0;
    for(size_t i=0; i<zenkit::DamageType::NUM; ++i)
      if(it.damage_type & (1<<i))
        ++n;
    for(size_t i=0; i<zenkit::DamageType::NUM && n>0; ++i)
      if(it.damage_type & (1<<i))
        dmg[i] = it.damage_total/n;
    }

  // the order of the plugin: barrier, blunt, edge, point, fire, magic, fly, fall
  static const struct { size_t id; const char* name; } order[] = {
    {zenkit::DamageType::BARRIER, "RX_INV_DESCWEAPON_DAM_BARRIER"},
    {zenkit::DamageType::BLUNT,   "RX_INV_DESCWEAPON_DAM_BLUNT"},
    {zenkit::DamageType::EDGE,    "RX_INV_DESCWEAPON_DAM_EDGE"},
    {zenkit::DamageType::POINT,   "RX_INV_DESCWEAPON_DAM_POINT"},
    {zenkit::DamageType::FIRE,    "RX_INV_DESCWEAPON_DAM_FIRE"},
    {zenkit::DamageType::MAGIC,   "RX_INV_DESCWEAPON_DAM_MAGIC"},
    {zenkit::DamageType::FLY,     "RX_INV_DESCWEAPON_DAM_FLY"},
    {zenkit::DamageType::FALL,    "RX_INV_DESCWEAPON_DAM_FALL"},
    };

  std::string ret = str(vm,"RX_INV_DESCWEAPON_DAM_MAIN");
  bool any = false;
  for(auto& o:order) {
    if(dmg[o.id]<=0)
      continue;
    ret += str(vm,o.name);
    ret += std::to_string(dmg[o.id]);
    ret += ",";
    any  = true;
    }
  if(!any)
    return "";
  ret.pop_back();
  return ret;
  }

// "Wymagana siła: 10 | zręczność: 5" or "Brak wymagań"
inline std::string requirements(zenkit::DaedalusVm& vm, const zenkit::IItem& it) {
  std::string ret = str(vm,"RX_INV_DESCWEAPON_REQ_MAIN");
  bool any = false;
  for(size_t i=0; i<zenkit::IItem::condition_count; ++i) {
    const int32_t atr = it.cond_atr[i];
    int32_t       val = it.cond_value[i];
    if(atr==0 || val==0)
      continue;
    any = true;
    const char* name = nullptr;
    switch(atr) {
      case 4:  name = "RX_INV_DESCWEAPON_REQ_STR";   break; // ATR_STRENGTH
      case 5:  name = "RX_INV_DESCWEAPON_REQ_DEX";   break; // ATR_DEXTERITY
      case 3:  name = "RX_INV_DESCWEAPON_REQ_MANA";  break; // ATR_MANA_MAX
      case 92: name = "RX_INV_DESCWEAPON_REQ_STA"; val*=10; break; // NB: stamina, x10
      case 89: name = "RX_INV_DESCWEAPON_REQ_BOW";   break; // NB: the bow talent
      case 91: name = "RX_INV_DESCWEAPON_REQ_CROSS"; break; // NB: the crossbow talent
      default: break;
      }
    if(name==nullptr)
      continue;
    ret += str(vm,name);
    ret += std::to_string(val);
    ret += " | ";
    }
  if(!any)
    return str(vm,"RX_INV_DESCWEAPON_REQ_NO");
  if(ret.size()>=3 && ret.compare(ret.size()-3,3," | ")==0)
    ret.resize(ret.size()-3);
  return ret;
  }

// "Jednoręczna", "Dwuręczna", "Łuk", ...
inline std::string weaponType(zenkit::DaedalusVm& vm, const zenkit::IItem& it) {
  using F = zenkit::ItemFlag;
  std::string ret;
  const auto mark = [&](const char* name) {
    ret += " | ";
    ret += str(vm,name);
    };
  if(has(it,uint32_t(F::TWO_HANDED_SWORD)) || has(it,uint32_t(F::TWO_HANDED_AXE)))
    ret += str(vm,"RX_INV_DESCWEAPON_TYPE_TWO");
  if(has(it,0x10003))
    ret  = str(vm,"RX_INV_DESCWEAPON_TYPE_RIGHT");
  if(has(it,0x40003))
    ret  = str(vm,"RX_INV_DESCWEAPON_TYPE_LEFT");
  if(has(it,uint32_t(F::SWORD)) || has(it,uint32_t(F::AXE)))
    ret += str(vm,"RX_INV_DESCWEAPON_TYPE_ONE");
  if(has(it,uint32_t(F::CROSSBOW)))
    ret += str(vm,"RX_INV_DESCWEAPON_TYPE_CROSS");
  if(has(it,uint32_t(F::BOW)))
    ret += str(vm,"RX_INV_DESCWEAPON_TYPE_BOW");

  // the bits of NB in C_ITEM.HP: BIT_ITEM_SHARPED 1, ORC_WEAPON 0x8000, UNDEAD_WEAPON 0x10000, MAG_SWORD 0x40000
  const uint32_t bits = uint32_t(it.hp);
  if(bits & 0x8000) {
    auto* sym  = vm.find_symbol_by_index(it.symbol_index());
    bool  drac = sym!=nullptr && (sym->name()=="ITMW_2H_DRACONSWORD_01" || sym->name()=="ITMW_2H_DRACONSWORD_02");
    mark(drac ? "RX_INV_DESCWEAPON_TYPE_LIZARD" : "RX_INV_DESCWEAPON_TYPE_ORC");
    }
  if(bits & 0x10000)
    mark("RX_INV_DESCWEAPON_TYPE_CURSED");
  if(bits & 0x40000)
    mark("RX_INV_DESCWEAPON_TYPE_MAGIC");
  if(bits & 0x1)
    mark("RX_INV_DESCWEAPON_TYPE_SHARPED");
  if(ret.rfind(" | ",0)==0)
    ret.erase(0,3);
  return ret;
  }

// "Ulepszenia: siła + 5"
inline std::string jewel(zenkit::DaedalusVm& vm, const zenkit::IItem& it) {
  for(size_t i=0; i<zenkit::IItem::condition_count; ++i) {
    if(it.change_value[i]==0)
      continue;
    const char* name = nullptr;
    switch(it.change_atr[i]) {
      case 4: name = "RX_INV_DESCJEWEL_STR";  break;
      case 5: name = "RX_INV_DESCJEWEL_DEX";  break;
      case 3: name = "RX_INV_DESCJEWEL_MANA"; break;
      default: break;
      }
    if(name==nullptr)
      continue;
    return str(vm,"RX_INV_DESCJEWEL_MAIN") + str(vm,name) + std::to_string(it.change_value[i]);
    }
  return "";
  }

inline void apply(zenkit::DaedalusVm& vm, zenkit::IItem& it) {
  if(!isNb(vm))
    return;

  constexpr int32_t CAT_NF    = 1<<1;
  constexpr int32_t CAT_FF    = 1<<2;
  constexpr int32_t CAT_ARMOR = 1<<4;
  constexpr int32_t CAT_MAGIC = int32_t(1u<<31);

  auto clear = [&]() { for(auto& c:it.count) c = 0; };
  // the weight: in the plugin it is a line of its own under the description
  auto putWeight = [&](int32_t w, bool keep) {
    if(keep && !it.text[4].empty())
      return;
    it.text[4]  = str(vm,"RX_INV_DESCWEAPON_WEIGHT") + std::to_string(w);
    it.count[4] = 0;
    };
  using F = zenkit::ItemFlag;
  const bool shield = it.main_flag==CAT_NF && has(it,0x40000) && !has(it,0x40003);

  if(it.main_flag==CAT_ARMOR) {
    clear();
    namespace D = zenkit::DamageType;
    it.text[0] = str(vm,"RX_INV_DESCARMOR_PROT_EDGE")  + std::to_string(it.protection[D::EDGE]);
    it.text[1] = str(vm,"RX_INV_DESCARMOR_PROT_BLUNT") + std::to_string(it.protection[D::BLUNT]);
    it.text[2] = str(vm,"RX_INV_DESCARMOR_PROT_POINT") + std::to_string(it.protection[D::POINT]);
    it.text[3] = str(vm,"RX_INV_DESCARMOR_PROT_FIRE")  + std::to_string(it.protection[D::FIRE]) + " | " +
                 str(vm,"RX_INV_DESCARMOR_PROT_MAGIC") + std::to_string(it.protection[D::MAGIC]);
    it.text[5] = requirements(vm,it);
    if(it.wear==1 || it.wear==2) // torso, head
      putWeight(it.weight,false);
    return;
    }

  if(shield) {
    putWeight(it.weight,true);
    return;
    }

  if(it.main_flag==CAT_NF || it.main_flag==CAT_FF) {
    clear();
    it.text[0] = damage(vm,it);
    if(it.main_flag==CAT_NF) {
      auto type = weaponType(vm,it);
      it.text[1] = str(vm,"RX_INV_DESCWEAPON_LEN") + std::to_string(it.range);
      if(!type.empty())
        it.text[1] += " | " + type;
      } else {
      it.text[1] = weaponType(vm,it);
      }
    it.text[2] = requirements(vm,it);
    it.text[5] = str(vm,"RX_INV_DESCWEAPON_PRICE");
    it.count[5] = it.value;
    const uint32_t inst = it.symbol_index();
    if(it.main_flag==CAT_NF)
      putWeight(weight(inst,0)+weight(inst,1),false); // RX_W_CRUSHINDEX + RX_W_MELEEWEAPONINDEX
    else
      putWeight(weight(inst,9),false);                // RX_W_RANGEWEAPONINDEX
    return;
    }

  if(it.main_flag==CAT_MAGIC) {
    if(!has(it,uint32_t(F::RING)) && !has(it,uint32_t(F::AMULET)))
      return;
    auto j = jewel(vm,it);
    if(!j.empty()) {
      it.text[0]  = j;
      it.count[0] = 0;
      }
    }
  }

}
