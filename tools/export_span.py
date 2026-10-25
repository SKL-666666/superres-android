# -*- coding: utf-8 -*-
"""SPAN 专用导出：先调用一次 forward 触发 Conv3XC 重参数化，再导出为单文件 ONNX"""
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
from span_arch import SPAN

m = SPAN(num_in_ch=3, num_out_ch=3, feature_channels=48, upscale=4)
sd = torch.load(os.path.join(D,"omd","4xNomosUni_span.pth"), map_location="cpu", weights_only=True)
m.load_state_dict(sd.get("params_ema", sd.get("params", sd)))
m.eval()

# 关键：Conv3XC.forward 会把 sk+dilation 融合进 weight_concat 并切换为 eval_conv
with torch.no_grad():
    _ = m(torch.rand(1,3,64,64))
def count_concat(mod):
    return sum(1 for k in mod.state_dict() if 'concat' in k)
print("concat buffers after warmup:", count_concat(m))

p = os.path.join(D,"onnx","span.onnx")
torch.onnx.export(m, torch.rand(1,3,128,128), p,
                  input_names=["in0"], output_names=["out0"],
                  dynamic_axes={"in0":{2:"h",3:"w"},"out0":{2:"h",3:"w"}},
                  opset_version=17, do_constant_folding=True, dynamo=False)
print("saved", p, os.path.getsize(p)/1024/1024, "MB")
