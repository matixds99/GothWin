// =====================================================================
// New Balance: stoły rzemieślnicze (alchemia, runy, zwoje, kowadło itd.)
//
// GDZIE: gamescript.cpp, funkcja initCommon(), wewnątrz bloku
//        if(... "RX_ISSPACETNET" ...) { ... }  dodanego przez Copilota,
//        zaraz POD liniami bindNewBalanceStub("RX_TOGGLEPLAYERCONTROLS", ...);
//
// CO ROBI: NB otwiera stoły przez RX_OpenCraft(...) - to okno z pluginu
//          Union, którego OpenGothic nie ma. W skryptach NB zostały jednak
//          stare menu dialogowe (PC_Health_Start, PC_Circle, PC_..._End itd.).
//          Tutaj RX_OpenCraft robi to samo co AI_ProcessInfos(hero) w
//          oryginalnym Gothicu 2, więc stół otwiera menu z przepisami.
// =====================================================================

    if(auto* sym = vm.find_symbol_by_name("RX_OPENCRAFT"); sym!=nullptr && sym->is_external()) {
      vm.register_external("RX_OPENCRAFT", [this](int32_t /*craftType*/) {
        if(auto pl = owner.player()) {
          aiOutOrderId = 0;
          pl->aiPush(AiQueue::aiProcessInfo(*pl));
          }
        });
      }
