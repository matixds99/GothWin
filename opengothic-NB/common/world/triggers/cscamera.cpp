#include "cscamera.h"

#include <Tempest/Log>
#include <cassert>
#include <cmath>
#include <algorithm>
#include "game/definitions/cameradefinitions.h"
#include "utils/dbgpainter.h"
#include "gothic.h"

using namespace Tempest;

static bool isFiniteVec(const Vec3& v) {
  return std::isfinite(v.x) && std::isfinite(v.y) && std::isfinite(v.z);
  }

static bool isFinitePt(const PointF& p) {
  return std::isfinite(p.x) && std::isfinite(p.y);
  }

Vec3 CsCamera::KeyFrame::position(float u) const {
  return ((c[0]*u + c[1])*u + c[2])*u + c[3];
  }

float CsCamera::KeyFrame::arcLength() const {
  float len  = 0;
  Vec3  prev = c[3];
  for(int i=1; i<=100; ++i) {
    Vec3 at = position(float(i)/100.f);
    len += (at-prev).length();
    prev = at;
    }
  return len;
  }


CsCamera::KbSpline::KbSpline(const std::vector<std::shared_ptr<zenkit::VCameraTrajectoryFrame>>& frames, const float duration, std::string_view vobName) {
  keyframe.reserve(frames.size());
  for(auto& f : frames) {
    KeyFrame kF;
    kF.c[3]  = Vec3(f->original_pose[3][0],f->original_pose[3][1],f->original_pose[3][2]);
    kF.time  = f->time;
    kF.motionType = f->motion_type;
    if(kF.time<0) {
      Log::e("CsCamera: \"", vobName, "\" - negative frame duration");
      }
    keyframe.push_back(kF);
    }

  fixupTimes(duration, vobName);

  const size_t size = keyframe.size();
  for(size_t i=0; i+1<size; ++i) {
    auto& kF0 = i==0 ? keyframe[i+0] : keyframe[i-1];
    auto& kF1 = keyframe[i+0];
    auto& kF2 = keyframe[i+1];
    auto& kF3 = i+2==size ? kF2 : keyframe[i+2];

    auto& p0  = kF0.c[3];
    auto& p1  = kF1.c[3];
    auto& p2  = kF2.c[3];
    auto& p3  = kF3.c[3];

    // NOTE: guard against degenerate (equal) key times - division by zero here
    // produces NaN camera position, which renders as completely black screen
    const float dt  = kF2.time-kF1.time;
    const float d0  = kF2.time-kF0.time;
    const float d1  = kF3.time-kF1.time;
    Vec3  dd  = d0>1e-5f ? (p2-p0) * dt/d0 : Vec3();
    Vec3  sd  = d1>1e-5f ? (p3-p1) * dt/d1 : Vec3();

    auto& kF  = keyframe[i];
    kF.c[0] =  2 * p1 - 2*p2 +   dd + sd;
    kF.c[1] = -3 * p1 + 3*p2 - 2*dd - sd;
    kF.c[2] = std::move(dd);
    }

  if(size<2)
    return;

  if(keyframe.front().time!=0) {
    Log::e("CsCamera: \"", vobName, "\" - invalid first frame");
    }
  }

void CsCamera::KbSpline::fixupTimes(const float duration, std::string_view vobName) {
  const size_t size = keyframe.size();
  if(size<2)
    return;

  bool valid = std::isfinite(keyframe[0].time) && keyframe[0].time>=0.f;
  for(size_t i=1; valid && i<size; ++i) {
    if(!std::isfinite(keyframe[i].time) || keyframe[i].time<=keyframe[i-1].time)
      valid = false;
    }
  if(valid)
    return;

  // ZenGin recomputes non-fixed key times automatically, so spacer-made cameras
  // often have garbage here (-1, 0, duplicates). Distribute keys along the path.
  Log::i("CsCamera: \"", vobName, "\" - invalid key times, redistributing over ", duration, "s");
  const float total = (std::isfinite(duration) && duration>0.f) ? duration : float(size-1);

  std::vector<float> len(size, 0.f);
  for(size_t i=1; i<size; ++i) {
    float l = (keyframe[i].c[3]-keyframe[i-1].c[3]).length();
    if(!std::isfinite(l))
      l = 0;
    len[i] = len[i-1] + l;
    }

  const float fullLen = len.back();
  for(size_t i=0; i<size; ++i) {
    if(fullLen>1e-3f)
      keyframe[i].time = total*len[i]/fullLen; else
      keyframe[i].time = total*float(i)/float(size-1);
    }

  // ensure strictly increasing (zero-length segments)
  for(size_t i=1; i<size; ++i) {
    if(keyframe[i].time<=keyframe[i-1].time)
      keyframe[i].time = keyframe[i-1].time + 1e-3f;
    }
  }

Vec3 CsCamera::KbSpline::position(const uint64_t time) const {
  const float t = float(time)/1000.f;

  if(keyframe.empty())
    return Vec3();
  if(keyframe.size()==1)
    return keyframe[0].c[3];

  if(t>=keyframe.back().time) {
    // duration may be longer then keyframe sequence
    return keyframe.back().position(1.f);
    }

  //TODO: lower bound
  uint32_t n = 0;
  while(n+1<keyframe.size() && t>=keyframe[n+1].time) {
    ++n;
    }

  auto& kF0 = keyframe[n];
  auto& kF1 = keyframe[n+1];
  const float span = kF1.time - kF0.time;
  float u   = span>1e-5f ? (t - kF0.time) / span : 1.f;
  u = std::clamp(u, 0.f, 1.f);

  const float k = 1.005f;
  if(kF0.motionType==zenkit::CameraMotion::SLOW && kF1.motionType!=zenkit::CameraMotion::SLOW) {
    // slow -> non-slow = accelerate
    u = std::pow(u, k);
    }
  else if(kF0.motionType!=zenkit::CameraMotion::SLOW && kF1.motionType==zenkit::CameraMotion::SLOW) {
    // non-slow -> slow = deccelerate
    u = std::pow(u, 1.f/k);
    }

  const Vec3 ret = keyframe[n].position(u);
  if(!isFiniteVec(ret))
    return kF0.c[3];
  return ret;
  }


CsCamera::CsCamera(Vob* parent, World& world, const zenkit::VCutsceneCamera& cam, Flags flags)
  :AbstractTrigger(parent,world,cam,flags) {
  if(cam.position_count<1 || cam.total_duration<=0)
    return;

  if(cam.trajectory_for==zenkit::CameraCoordinateReference::OBJECT || cam.target_trajectory_for==zenkit::CameraCoordinateReference::OBJECT) {
    Log::d("Object camera not implemented, \"", name() , "\"");
    return;
    }

  duration      = uint64_t(cam.total_duration * 1000.f);
  delay         = uint64_t(cam.auto_untrigger_last_delay * 1000.f);
  autoUntrigger = cam.auto_untrigger_last;
  playerMovable = cam.auto_player_movable;

  posSpline    = KbSpline(cam.trajectory_frames, cam.total_duration, cam.vob_name);
  targetSpline = KbSpline(cam.target_frames, cam.total_duration, cam.vob_name);

  Log::i("CsCamera: \"", cam.vob_name, "\" duration=", cam.total_duration,
         " pos=", posSpline.size(), " target=", targetSpline.size(),
         " autoUntrigger=", int(autoUntrigger), " delay=", cam.auto_untrigger_last_delay);
  }

bool CsCamera::isPlayerMovable() const {
  return playerMovable;
  }

void CsCamera::debugDraw(DbgPainter& p) const {
  static bool dbg = false;
  if(!dbg)
    return;

  if(posSpline.size()<=1)
    return;

  auto at = posSpline.keyframe[0].position(0);
  for(size_t i=1; i<posSpline.size(); ++i) {
    auto& k   = posSpline.keyframe[i];
    p.setPen(Color(0,0,1));
    for(size_t r=0; r<100; ++r) {
      auto  pos = k.position(float(r)/100.f);
      p.drawLine(at, pos);
      at = pos;
      }

    auto  pt = k.position(0);
    float l  = 30;
    p.setPen(Color(1,0,1));
    p.drawLine(pt-Vec3(l,0,0),pt+Vec3(l,0,0));
    p.drawLine(pt-Vec3(0,l,0),pt+Vec3(0,l,0));
    p.drawLine(pt-Vec3(0,0,l),pt+Vec3(0,0,l));
    }
  }

void CsCamera::onTrigger(const TriggerEvent& evt) {
  if(hasTicksEnabled() || posSpline.size()==0)
    return;

  if(auto cs = world.currentCs())
    cs->onUntrigger(evt);

  timeTrigger = world.tickCount();
  world.setCurrentCs(this);
  world.script().dmaCsCamera(true);   // mods of Ikarus and LeGo hook the cameras of the cutscenes
  enableTicks();

  auto& camera = world.gameSession().camera();
  if(!camera.isCutscene()) {
    auto cPos = position();
    if(!isFiniteVec(cPos)) {
      Log::e("CsCamera: \"", vobName, "\" - invalid camera position, cutscene skipped");
      disableTicks();
      world.setCurrentCs(nullptr);
      world.script().dmaCsCamera(false);
      return;
      }
    camera.setMode(Camera::Mode::Cutscene);
    camera.setPosition(cPos);
    auto ang = spin(cPos);
    if(isFinitePt(ang))
      camera.setAngles(ang);
    }
  }

void CsCamera::onUntrigger(const TriggerEvent& evt) {
  if(!hasTicksEnabled())
    return;
  disableTicks();

  if(world.currentCs()!=this)
    return;

  world.setCurrentCs(nullptr);
  world.script().dmaCsCamera(false);

  auto& camera = world.gameSession().camera();
  camera.setMode(Camera::Mode::Normal);
  if(auto pl = Gothic::inst().player()) {
    camera.reset(pl);
    } else {
    camera.setMarvinMode(Camera::M_Free);
    }

  if(Gothic::inst().isBenchmarkMode())
    Gothic::inst().onBenchmarkFinished();
  }

void CsCamera::tick(uint64_t /*dt*/) {
  const uint64_t time = world.tickCount() - timeTrigger;
  if(time>duration+delay && (autoUntrigger || vobName=="TIMEDEMO")) {
    TriggerEvent e("","",TriggerEvent::T_Untrigger);
    onUntrigger(e);
    return;
    }

  if(time>duration)
    return;

  auto& camera = world.gameSession().camera();
  if(camera.isCutscene()) {
    auto cPos = position();
    if(!isFiniteVec(cPos)) {
      // never leave the camera in NaN state - this is a black screen
      Log::e("CsCamera: \"", vobName, "\" - invalid camera position, cutscene aborted");
      TriggerEvent e("","",TriggerEvent::T_Untrigger);
      onUntrigger(e);
      return;
      }
    camera.setPosition(cPos);
    auto ang = spin(cPos);
    if(isFinitePt(ang))
      camera.setAngles(ang);
    }
  }

Vec3 CsCamera::position() {
  Vec3 pos;
  if(posSpline.size()==1) {
    pos = posSpline.keyframe[0].c[3];
    } else {
    const uint64_t time = std::min(world.tickCount() - timeTrigger, duration);
    pos = posSpline.position(time);
    }
  return pos;
  }

PointF CsCamera::spin(Tempest::Vec3& d) {
  if(targetSpline.size()==0)
    d = d - Gothic::inst().camera()->originLwc();
  else if(targetSpline.size()==1)
    d = targetSpline.keyframe[0].c[3] - d;
  else if(targetSpline.size()>1) {
    const uint64_t time = std::min(world.tickCount() - timeTrigger, duration);
    d = targetSpline.position(time) - d;
    }

  const float len = d.length();
  if(!(len>1e-3f) || !isFiniteVec(d)) {
    // camera and target in same point: direction is undefined
    return PointF(NAN, NAN);
    }

  float k     = 180.f/float(M_PI);
  float spinX = k * std::asin(std::clamp(d.y/len, -1.f, 1.f));
  float spinY = 0;
  if(d.x!=0.f || d.z!=0.f)
    spinY = k * std::atan2(d.z,d.x);

  return {-spinX, spinY};
  }
