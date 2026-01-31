import { useEffect, useRef } from 'react';

interface WordCloudProps {
  data: Array<{ word: string; count: number }>;
  width?: number;
  height?: number;
}

/**
 * 词云图组件（简单实现）
 */
function WordCloud({ data, width = 800, height = 400 }: WordCloudProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!canvasRef.current || data.length === 0) return;

    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    canvas.width = width;
    canvas.height = height;
    ctx.clearRect(0, 0, width, height);

    // 计算最大和最小计数
    const counts = data.map(d => d.count);
    const maxCount = Math.max(...counts);
    const minCount = Math.min(...counts);

    // 简单的词云布局算法
    const words = data.map(item => ({
      text: item.word,
      size: 12 + ((item.count - minCount) / (maxCount - minCount)) * 24,
      x: Math.random() * (width - 100),
      y: Math.random() * (height - 50),
    }));

    // 绘制文字
    words.forEach(word => {
      ctx.font = `${word.size}px Arial`;
      ctx.fillStyle = `hsl(${Math.random() * 360}, 70%, 50%)`;
      ctx.fillText(word.text, word.x, word.y);
    });
  }, [data, width, height]);

  return <canvas ref={canvasRef} style={{ width: '100%', height: '100%' }} />;
}

export default WordCloud;
