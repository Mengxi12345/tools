# Colima 和 QEMU 安装状态

## 当前状态

### ⏳ QEMU 安装中
QEMU 是一个大型软件包，安装可能需要 10-30 分钟，取决于网络速度和系统性能。

### 安装命令
```bash
brew install qemu
```

### 检查安装状态
```bash
# 检查是否安装完成
which qemu-img
qemu-img --version

# 检查安装进程
ps aux | grep -i qemu | grep -v grep
```

### 安装完成后启动 Colima
```bash
colima start --cpu 2 --memory 4
```

首次启动可能需要 2-5 分钟来下载和设置虚拟机镜像。

## 更新时间
2026-01-25 20:30
