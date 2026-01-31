import { useState, useEffect, useRef, useMemo } from 'react';
import { Table } from 'antd';
import type { TableProps } from 'antd/es/table';

interface VirtualTableProps<T> extends Omit<TableProps<T>, 'scroll'> {
  height?: number;
  itemHeight?: number;
}

/**
 * 虚拟滚动表格组件（适用于大数据量列表）
 */
function VirtualTable<T extends Record<string, any>>({
  dataSource = [],
  height = 400,
  itemHeight = 50,
  ...restProps
}: VirtualTableProps<T>) {
  const [visibleRange, setVisibleRange] = useState({ start: 0, end: 20 });
  const containerRef = useRef<HTMLDivElement>(null);

  const visibleData = useMemo(() => {
    return dataSource.slice(visibleRange.start, visibleRange.end);
  }, [dataSource, visibleRange]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const scrollTop = container.scrollTop;
      const start = Math.floor(scrollTop / itemHeight);
      const end = Math.min(start + Math.ceil(height / itemHeight) + 5, dataSource.length);
      
      setVisibleRange({ start, end });
    };

    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [dataSource.length, height, itemHeight]);

  return (
    <div
      ref={containerRef}
      style={{
        height,
        overflow: 'auto',
        position: 'relative',
      }}
    >
      <div style={{ height: dataSource.length * itemHeight, position: 'relative' }}>
        <div
          style={{
            transform: `translateY(${visibleRange.start * itemHeight}px)`,
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
          }}
        >
          <Table
            {...restProps}
            dataSource={visibleData}
            pagination={false}
            scroll={{ y: height }}
          />
        </div>
      </div>
    </div>
  );
}

export default VirtualTable;
