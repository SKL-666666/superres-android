# -*- coding: utf-8 -*-
"""v14: 单图全图四拼一超大对比 (av3/x4v3/wdn/SPAN)"""
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

def imread_u(p): return cv2.imdecode(np.fromfile(p,dtype=np.uint8), cv2.IMREAD_UNCHANGED)
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

P = r"C:\Users\yifen\Pictures\Screenshots\屏幕截图 2026-08-17 181654.png"
im = imread_u(P)
if im.shape[2]==4:
    im = cv2.cvtColor(im, cv2.COLOR_BGRA2BGR)
H,W = im.shape[:2]
print(f"[in] {W}x{H}", flush=True)

def tile_sr(model, img, tile=384, pad=8):
    h,w = img.shape[:2]
    out = np.zeros((h*4,w*4,3),np.uint8)
    with torch.no_grad():
        for y in range(0,h,tile):
            for x in range(0,w,tile):
                y0,x0=max(0,y-pad),max(0,x-pad); y1,x1=min(h,y+tile+pad),min(w,x+tile+pad)
                o = model(to_t(img[y0:y1,x0:x1])).squeeze(0)
                oy,ox=(y-y0)*4,(x-x0)*4
                oh=min(o.shape[1]-oy,tile*4); ow=min(o.shape[2]-ox,tile*4)
                out[y*4:y*4+oh, x*4:x*4+ow] = tb(o[:,oy:oy+oh,ox:ox+ow])
    return out

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

ref2x = cv2.resize(im,(W*2,H*2),interpolation=cv2.INTER_CUBIC)
tiles=[]
for k in models:
    t0=time.time(); full4 = tile_sr(models[k], im); dt=time.time()-t0
    full2 = cv2.resize(full4,(W*2,H*2),interpolation=cv2.INTER_AREA)
    del full4
    p_,s_ = psnr(full2,ref2x), ssim(full2,ref2x)
    sh = cv2.Laplacian(cv2.cvtColor(full2,cv2.COLOR_BGR2GRAY),cv2.CV_64F).var()
    print(f"{k:6s} full {W*H/1e6:.2f}MP@4x: {dt:6.1f}s | vs bicubic2x PSNR {p_:5.2f} SSIM {s_:.4f} sharp {sh:6.1f}", flush=True)
    lab = f"{k}   PSNR {p_:.2f}  SSIM {s_:.4f}  {dt:.0f}s"
    bar = np.full((44,full2.shape[1],3),0,np.uint8)
    cv2.putText(bar,lab,(14,31),cv2.FONT_HERSHEY_SIMPLEX,1.0,(255,255,255),2,cv2.LINE_AA)
    tiles.append(np.vstack([bar,full2]))
grid = np.vstack([np.hstack([tiles[0],tiles[1]]), np.hstack([tiles[2],tiles[3]])])
outp = os.path.join(D,"full_compare_4in1.jpg")
imwrite_u(outp, grid, 90)
print("[done]", outp, grid.shape, f"{os.path.getsize(outp)//1024}KB")
