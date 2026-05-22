#!/usr/bin/env python3
"""
RMBG-1.4 背景移除模型 → TFLite 转换脚本

将此脚本放到项目根目录运行：
    pip install torch onnx onnxruntime onnx2tf tensorflow
    python download_model.py

生成的 rmbg.tflite 会自动放入 app/src/main/assets/
"""

import os
import sys
import shutil
import urllib.request
from pathlib import Path

MODEL_URL = "https://huggingface.co/briaai/RMBG-1.4/resolve/main/model.onnx"
MODEL_NAME = "rmbg.onnx"
OUTPUT_TFLITE = "rmbg.tflite"
TARGET_DIR = Path("app/src/main/assets")
PROJECT_ROOT = Path(__file__).parent.resolve()


def download_file(url: str, dest: Path) -> None:
    """下载文件并显示进度"""
    print(f"⬇ 下载模型: {url}")
    dest.parent.mkdir(parents=True, exist_ok=True)

    def _progress(count, block_size, total_size):
        pct = int(count * block_size * 100 / total_size) if total_size > 0 else 0
        sys.stdout.write(f"\r  {pct}% ({count * block_size // 1024 // 1024}MB)")
        sys.stdout.flush()

    urllib.request.urlretrieve(url, str(dest), _progress)
    print("\n  ✓ 下载完成")


def convert_onnx_to_tflite(onnx_path: Path, tflite_path: Path) -> None:
    """使用 onnx2tf 将 ONNX 转为 TFLite"""
    print("🔄 转换 ONNX → TFLite（可能需要几分钟）...")

    try:
        import onnx2tf
    except ImportError:
        print("❌ 请先安装 onnx2tf: pip install onnx2tf tensorflow")
        sys.exit(1)

    import onnx
    model = onnx.load(str(onnx_path))
    print(f"  ONNX 输入: {[i.name for i in model.graph.input]}")
    print(f"  ONNX 输出: {[o.name for o in model.graph.output]}")

    cmd = [
        "onnx2tf",
        "-i", str(onnx_path),
        "-o", str(tflite_path.parent),
        "-osd",  # 输出签名定义
        "-ois", "input:1,3,1024,1024",  # 输入形状 NCHW
        "-v",  # 详细日志
    ]

    os.system(" ".join(cmd))

    # onnx2tf 会生成 model_float32.tflite，重命名
    generated = tflite_path.parent / "model_float32.tflite"
    if generated.exists():
        shutil.move(str(generated), str(tflite_path))
        print(f"  ✓ TFLite 模型已生成: {tflite_path.name}")
    else:
        print("⚠ onnx2tf 输出文件名未知，请检查生成的文件")
        for f in tflite_path.parent.glob("*.tflite"):
            print(f"  找到: {f}")


def alternative_onnxruntime_convert(onnx_path: Path, tflite_path: Path) -> None:
    """
    备选方案: 使用 onnx → tf saved_model → tflite
    更稳定但步骤更多
    """
    print("🔄 使用备选方案转换 (ONNX → SavedModel → TFLite) ...")

    try:
        import onnx
        from onnx_tf.backend import prepare
    except ImportError:
        print("❌ 请先安装: pip install onnx-tf tensorflow")
        sys.exit(1)

    onnx_model = onnx.load(str(onnx_path))
    tf_rep = prepare(onnx_model)

    saved_model_dir = onnx_path.parent / "saved_model"
    tf_rep.export_graph(str(saved_model_dir))

    import tensorflow as tf
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]

    tflite_model = converter.convert()
    tflite_path.write_bytes(tflite_model)
    print(f"  ✓ TFLite 已生成: {tflite_path.name}")


def main() -> None:
    os.chdir(PROJECT_ROOT)

    onnx_path = PROJECT_ROOT / MODEL_NAME
    tflite_path = TARGET_DIR / OUTPUT_TFLITE

    # 1. 下载 ONNX 模型
    if not onnx_path.exists():
        download_file(MODEL_URL, onnx_path)
    else:
        print(f"✓ ONNX 模型已存在: {onnx_path}")

    # 2. 转换 ONNX → TFLite
    if tflite_path.exists():
        print(f"✓ TFLite 模型已存在: {tflite_path}")
        print(f"  文件大小: {tflite_path.stat().st_size // 1024 // 1024}MB")
        return

    TARGET_DIR.mkdir(parents=True, exist_ok=True)

    try:
        convert_onnx_to_tflite(onnx_path, tflite_path)
    except Exception as e:
        print(f"⚠ onnx2tf 转换失败: {e}")
        print("  尝试备选方案...")
        try:
            alternative_onnxruntime_convert(onnx_path, tflite_path)
        except Exception as e2:
            print(f"❌ 备选方案也失败了: {e2}")
            print("\n📋 手动转换步骤:")
            print("1. pip install onnx2tf tensorflow")
            print("2. onnx2tf -i rmbg.onnx -o app/src/main/assets/"
                  " -ois input:1,3,1024,1024")
            print("3. 将生成的 model_float32.tflite 重命名为 rmbg.tflite")
            sys.exit(1)

    # 3. 验证
    if tflite_path.exists():
        size_mb = tflite_path.stat().st_size / (1024 * 1024)
        print(f"\n✅ 完成! {tflite_path} ({size_mb:.1f}MB)")
        print(f"   模型位置: {tflite_path}")
        print(f"   重新构建 APK 即可使用抠图功能")
    else:
        print(f"\n❌ 转换失败，请检查错误日志")


if __name__ == "__main__":
    main()
