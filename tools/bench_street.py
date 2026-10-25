# -*- coding: utf-8 -*-
"""v12: 街景手动选区对比 (av3/x4v3/wdn/SPAN)"""
import cv2, time, os, sys, types
import numpy as np
import torch
torch.set_num_threads(4)
D = r"C:\Users\yifen\Desktop\sr_test"
sys.path.insert(0, D)
stub = types.ModuleType("basicsr"); u = types.ModuleType("basicsr.utils")
r = types.ModuleType("basicsr.utils.registry")
class Reg:
    def register(self, *a):
        if a: m=a[0]; m.registered=True; return m
        def deco(m): m.registered=True; return m
        return deco
r.ARCH_REGISTRY = Reg(); u.registry = r; stub.utils = u
sys.modules.update({"basicsr":stub,"basicsr.utils":u,"basicsr.utils.registry":r})
from srvgg_arch import SRVGGNetCompact
from span_arch import SPAN

def imread_u(p): return cv2.imdecode(np.fromfile(p,dtype=np.uint8), cv2.IMREAD_COLOR)
def imwrite_u(p,img,q=95):
    ok,buf=cv2.imencode(os.path.splitext(p)[1],img,[cv2.IMWRITE_JPEG_QUALITY,q]); buf.tofile(p)
def to_t(b):
    return torch.from_numpy(cv2.cvtColor(b,cv2.COLOR_BGR2RGB).transpose(2,0,1).astype(np.float32)/255.).unsqueeze(0)
def tb(t):
    if t.dim()==4: t=t.squeeze(0)
    a=(t.permute(1,2,0).clamp_(0,1).numpy()*255+0.5).astype(np.uint8)
    return cv2.cvtColor(a, cv2.COLOR_RGB2BGR)

m_av3 = SRVGGNetCompact(num_feat=64, num_conv=16, upscale=4, act_type='prelu')
m_av3.load_state_dict(torch.load(os.path.join(D,"realesr-animevideov3.pth"),map_location="cpu",weights_only=True)["params"]); m_av3.eval()
m_v3 = SRVGGNetCompact(num_feat=64, num_conv=32, upscale=4, act_type='prelu')
m_v3.load_state_dict(torch.load(os.path.join(D,"realesr-general-x4v3.pth"),map_location="cpu",weights_only=True)["params"]); m_v3.eval()
m_wd = SRVGGNetCompact(num_feat=64, num_conv=32, upscale=4, act_type='prelu')
m_wd.load_state_dict(torch.load(os.path.join(D,"realesr-general-wdn-x4v3.pth"),map_location="cpu",weights_only=True)["params"]); m_wd.eval()
sd = torch.load(os.path.join(D,"omd","4xNomosUni_span.pth"), map_location="cpu", weights_only=True)
m_sp = SPAN(num_in_ch=3, num_out_ch=3, feature_channels=48, upscale=4)
m_sp.load_state_dict(sd.get("params_ema", sd.get("params", sd))); m_sp.eval()
models = {"av3":m_av3,"x4v3":m_v3,"wdn":m_wd,"SPAN":m_sp}
methods = list(models)

def psnr(a,b):
    m=np.mean((a.astype(np.float64)-b.astype(np.float64))**2)
    return 99.0 if m<1e-9 else 10*np.log10(255**2/m)
def ssim(a,b):
    C1,C2=(0.01*255)**2,(0.03*255)**2; ss=[]
    for ch in range(3):
        x=a[...,ch].astype(np.float64); y=b[...,ch].astype(np.float64)
        mx=cv2.GaussianBlur(x,(11,11),1.5); my=cv2.GaussianBlur(y,(11,11),1.5)
        vx=cv2.GaussianBlur(x*x,(11,11),1.5)-mx*mx; vy=cv2.GaussianBlur(y*y,(11,11),1.5)-my*my
        vxy=cv2.GaussianBlur(x*y,(11,11),1.5)-mx*my
        ss.append((((2*mx*my+C1)*(2*vxy+C2))/((mx*mx+my*my+C1)*(vx+vy+C2))).mean())
    return float(np.mean(ss))

# 手动选区（4MP 坐标系比例）: (文件, 标签, xf, yf) 取 600x600
cases = [
    ("s_road.jpg",  "neon+Kodak",   0.72, 0.28),   # 右上 Kodak 广告牌文字
    ("s_town.jpg",  "cathedral",    0.22, 0.30),   # 圣保罗教堂穹顶+雕塑
    ("s_walk.jpg",  "skyline",      0.38, 0.62),   # 曼哈顿楼群
    ("street_b.jpg","towers",       0.60, 0.55),   # 芝加哥高楼窗格
]
CS=600
rows=[]; agg={k:[[],[]] for k in methods}; tavg={k:[] for k in methods}
for f,label,xf,yf in cases:
    img12 = imread_u(os.path.join(D,"imgs",f)) if f.startswith("s_") else imread_u(os.path.join(D,f))
    H12,W12 = img12.shape[:2]
    s = np.sqrt(4_000_000/(H12*W12))
    img4 = cv2.resize(img12,(int(W12*s),int(H12*s)),interpolation=cv2.INTER_CUBIC)
    H4,W4 = img4.shape[:2]
    x0 = int(np.clip(W4*xf, 0, W4-CS)); y0 = int(np.clip(H4*yf, 0, H4-CS))
    crop = img4[y0:y0+CS, x0:x0+CS].copy()
    ref = cv2.resize(img12[int(y0/s):int((y0+CS)/s), int(x0/s):int((x0+CS)/s)], (CS*2,CS*2), interpolation=cv2.INTER_CUBIC)
    del img12, img4
    disp=[]; print(f"--- {label} ({f}) ---", flush=True)
    with torch.no_grad():
        for k in methods:
            t0=time.time(); o=tb(models[k](to_t(crop))); dt=time.time()-t0
            o2=cv2.resize(o,(CS*2,CS*2),interpolation=cv2.INTER_AREA)
            tavg[k].append(dt)
            p_,s_=psnr(o2,ref),ssim(o2,ref)
            agg[k][0].append(p_); agg[k][1].append(s_)
            print(f"  {k:6s} PSNR {p_:5.2f} SSIM {s_:.4f} t {dt:5.2f}s", flush=True)
            cy,cx=(CS-250),(CS-250)  # 取右上细节
            tt=cv2.resize(o2[cy-150:cy+150, cx-150:cx+150],(600,600),interpolation=cv2.INTER_NEAREST)
            cv2.rectangle(tt,(0,0),(160,30),(0,0,0),-1)
            cv2.putText(tt,k,(6,21),cv2.FONT_HERSHEY_SIMPLEX,0.6,(255,255,255),1,cv2.LINE_AA)
            disp.append(tt)
    row=np.hstack(disp)
    cv2.putText(row,label,(8,row.shape[0]-10),cv2.FONT_HERSHEY_SIMPLEX,0.8,(0,255,255),2,cv2.LINE_AA)
    rows.append(row)
imwrite_u(os.path.join(D,"compare_street3.jpg"), np.vstack(rows))
print("\n===== avg =====")
for k in methods:
    print(f"{k:6s} PSNR {np.mean(agg[k][0]):5.2f} SSIM {np.mean(agg[k][1]):.4f} crop-time {np.mean(tavg[k]):5.2f}s")
print("[done] compare_street3.jpg")
