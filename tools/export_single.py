# -*- coding: utf-8 -*-
"""重新导出 3 个 SRVGG 模型为单文件 ONNX（内嵌权重）"""
import os, sys, types
import torch
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

def build_compact(key, nc):
    m = SRVGGNetCompact(num_feat=64, num_conv=nc, upscale=4, act_type='prelu')
    sd = torch.load(os.path.join(D, key+".pth"), map_location="cpu", weights_only=True)
    m.load_state_dict(sd["params"]); m.eval(); return m

for name, key, nc in [("animevideov3","realesr-animevideov3",16),
                      ("x4v3","realesr-general-x4v3",32),
                      ("wdn","realesr-general-wdn-x4v3",32)]:
    m = build_compact(key, nc)
    p = os.path.join(D,"onnx",name+"_single.onnx")
    torch.onnx.export(m, torch.rand(1,3,128,128), p,
                      input_names=["in0"], output_names=["out0"],
                      dynamic_axes={"in0":{2:"h",3:"w"},"out0":{2:"h",3:"w"}},
                      opset_version=17, do_constant_folding=True,
                      dynamo=False,   # 关掉 dynamo，避免 external data 分片
                      )
    print(f"{name:14s} {os.path.getsize(p)/1024/1024:.2f} MB")
