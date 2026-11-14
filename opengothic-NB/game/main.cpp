#include <Tempest/Window>
#include <Tempest/Application>
#include <Tempest/Log>
#include <zenkit/Logger.hh>

#include <Tempest/VulkanApi>

#if defined(_MSC_VER)
#include <Tempest/DirectX12Api>
#endif

#if defined(__APPLE__)
#include <Tempest/MetalApi>
#endif

#if defined(__IOS__) || defined(__ANDROID__)
#include "utils/installdetect.h"
#include <filesystem>
#include <cstdlib>
#include <cstdio>
#endif

#if defined(__ANDROID__)
#include <Tempest/TextCodec>
#endif

#include "utils/crashlog.h"
#include "utils/gamelog.h"
#include "graphics/safegpu.h"
#include "mainwindow.h"
#include "gothic.h"
#include "build.h"
#include "commandline.h"

#include <dmusic.h>

std::string_view selectDevice(const Tempest::AbstractGraphicsApi& api) {
  auto d = api.devices();

  static Tempest::Device::Props p;
  for(auto& i:d)
    if(i.type==Tempest::DeviceType::Discrete) {
      p = i;
      return p.name;
      }

  if(d.size()>0) {
    p = d[0];
    return p.name;
    }

  return "";
  }

std::unique_ptr<Tempest::AbstractGraphicsApi> mkApi(const CommandLine& g) {
  Tempest::ApiFlags flg = g.isValidationMode()
      ? Tempest::ApiFlags::Validation
      : Tempest::ApiFlags::NoFlags;

  switch(g.graphicsApi()) {
    case CommandLine::DirectX12:
#if defined(_MSC_VER)
      return std::make_unique<Tempest::DirectX12Api>(flg);
#else
      break;
#endif

    case CommandLine::Vulkan:
#if !defined(__APPLE__)
      return std::make_unique<Tempest::VulkanApi>(flg);
#else
      break;
#endif
    }

#if defined(__APPLE__)
  return std::make_unique<Tempest::MetalApi>(flg);
#else
  return std::make_unique<Tempest::VulkanApi>(flg);
#endif
  }

#if defined(__ANDROID__)
static Tempest::WFile* appLogFile = nullptr;
#endif

int main(int argc, const char** argv) {
#if defined(__ANDROID__)
  {
    InstallDetect install;
    const auto game = install.detectG2();

    if(game.empty()) {
      std::fprintf(stderr, "OpenGothic: game folder is missing or inaccessible\n");
      return 1;
      }

    std::error_code ec;
    std::filesystem::current_path(
        std::filesystem::path(Tempest::TextCodec::toUtf8(game)), ec);

    if(ec) {
      std::fprintf(stderr, "OpenGothic: cannot use game folder: %s\n",
                   ec.message().c_str());
      return 1;
      }

    // NB fix 37: a longer buffer of the sound. OpenAL mixes 3 portions of 20 ms ahead (60 ms) and during the loading of the
    // world it did not keep up: the sound stopped for a moment. 6 portions (120 ms). The config is read once, at the first
    // call of OpenAL, so it is written here (in the folder of the game, next to log.txt), before anything opens the sound device.
    if(std::FILE* f = std::fopen("alsoft.conf","wb")) {
      std::fputs("[general]\nperiod_size = 960\nperiods = 6\n",f);
      std::fclose(f);
      const auto conf = (std::filesystem::current_path(ec)/"alsoft.conf").string();
      if(!ec)
        setenv("ALSOFT_CONF",conf.c_str(),1);
      }
  }
#elif defined(__IOS__)
  {
    auto appdir = InstallDetect::applicationSupportDirectory();
    std::filesystem::current_path(appdir);
  }
#endif

  try {
    // Android: katalog roboczy jest juz ustawiony na folder gry (wyzej),
    // wiec log.txt laduje obok crash.log w folderze gry.
    static Tempest::WFile logFile("log.txt");

#if defined(__ANDROID__)
    appLogFile = &logFile;
#endif

    Tempest::Log::setOutputCallback([](Tempest::Log::Mode mode, const char* text) {
      GameLog::push(
          mode==Tempest::Log::Error ? GameLog::Error :
          mode==Tempest::Log::Debug ? GameLog::Debug :
                                     GameLog::Info,
          text);

      logFile.write(text,std::strlen(text));
      logFile.write("\n",1);

      if(mode==Tempest::Log::Error)
        logFile.flush();

#if defined(__ANDROID__)
      if(mode==Tempest::Log::Debug) {
        SafeGpu::onLog(text);
        logFile.flush();
        }
#endif
      });
    }
  catch(...) {
    Tempest::Log::setOutputCallback([](Tempest::Log::Mode mode, const char* text) {
      GameLog::push(
          mode==Tempest::Log::Error ? GameLog::Error :
          mode==Tempest::Log::Debug ? GameLog::Debug :
                                     GameLog::Info,
          text);
      std::fprintf(stderr,"%s\n",text);
      });

    Tempest::Log::e("unable to setup logfile - fallback to console log");
    }

  CrashLog::setup();

  zenkit::Logger::set(
      zenkit::LogLevel::INFO,
      [] (zenkit::LogLevel lvl, const char* cat, const char* message) {
        (void)cat;

        switch(lvl) {
          case zenkit::LogLevel::ERROR:
            Tempest::Log::e("[zenkit] ", message);
            break;

          case zenkit::LogLevel::WARNING:
            Tempest::Log::e("[zenkit] ", message);
            break;

          case zenkit::LogLevel::INFO:
            Tempest::Log::i("[zenkit] ", message);
            break;

          case zenkit::LogLevel::DEBUG:
          case zenkit::LogLevel::TRACE:
            Tempest::Log::d("[zenkit] ", message);
            break;
          }
      });

  Dm_setLogger(DmLogLevel_INFO, [](void* ctx, DmLogLevel lvl, char const* msg) {
    switch(lvl) {
      case DmLogLevel_FATAL:
      case DmLogLevel_ERROR:
      case DmLogLevel_WARN:
        Tempest::Log::e("[dmusic] ", msg);
        break;

      case DmLogLevel_INFO:
        Tempest::Log::i("[dmusic] ", msg);
        break;

      case DmLogLevel_DEBUG:
      case DmLogLevel_TRACE:
        Tempest::Log::d("[dmusic] ", msg);
        break;
      }
    }, nullptr);

  Tempest::Log::i(appBuild);
  Workers::setThreadName("Main thread");

  CommandLine          cmd{argc,argv};
  auto                 api = mkApi(cmd);
  const auto           gpuName = selectDevice(*api);
  CrashLog::setGpu(gpuName);

  Tempest::Device      device{*api,gpuName};
  CrashLog::setGpu(device.properties().name);

  Resources            resources{device};
  Gothic               gothic;
  GameMusic            music;
  gothic.setupGlobalScripts();

  MainWindow           wx(device);
  Tempest::Application app;
  const int ret = app.exec();

#if defined(__ANDROID__)
  Tempest::Log::i("exit ",ret);
  if(appLogFile!=nullptr)
    appLogFile->flush();
  std::_Exit(ret);
#endif

  return ret;
  }