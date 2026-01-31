import { useState, useEffect, useRef } from 'react';
import { Input, Button, Dropdown, List, Space, Tag } from 'antd';
import { SearchOutlined, HistoryOutlined, CloseOutlined } from '@ant-design/icons';
import { contentApi } from '../../services/api';
import './SearchBar.css';

const { Search } = Input;

interface SearchBarProps {
  onSearch: (value: string) => void;
  placeholder?: string;
  showHistory?: boolean;
}

interface SearchHistory {
  id: string;
  query: string;
  searchType: string;
  resultCount: number;
  createdAt: string;
}

function SearchBar({ onSearch, placeholder = '搜索内容...', showHistory = true }: SearchBarProps) {
  const [searchValue, setSearchValue] = useState('');
  const [historyVisible, setHistoryVisible] = useState(false);
  const [searchHistory, setSearchHistory] = useState<SearchHistory[]>([]);
  const [popularQueries, setPopularQueries] = useState<string[]>([]);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (showHistory) {
      loadSearchHistory();
      loadPopularQueries();
    }
  }, [showHistory]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setHistoryVisible(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const loadSearchHistory = async () => {
    try {
      const response: any = await contentApi.getSearchHistory({ limit: 10 });
      if (response.code === 200) {
        const list = response.data?.content ?? (Array.isArray(response.data) ? response.data : []);
        setSearchHistory(list);
      }
    } catch (error) {
      console.error('加载搜索历史失败', error);
    }
  };

  const loadPopularQueries = async () => {
    try {
      const response: any = await contentApi.getPopularSearchQueries({ limit: 5 });
      if (response.code === 200) {
        setPopularQueries(Array.isArray(response.data) ? response.data : []);
      }
    } catch (error) {
      console.error('加载热门搜索失败', error);
    }
  };

  const handleSearch = (value: string) => {
    if (value.trim()) {
      onSearch(value.trim());
      setHistoryVisible(false);
      if (showHistory) {
        loadSearchHistory();
      }
    }
  };

  const handleHistoryClick = (query: string) => {
    setSearchValue(query);
    handleSearch(query);
  };

  const handleClearHistory = async () => {
    try {
      // 这里可以添加清除历史的API调用
      setSearchHistory([]);
    } catch (error) {
      console.error('清除历史失败', error);
    }
  };

  const historyMenu = (
    <div className="search-history-dropdown">
      {popularQueries.length > 0 && (
        <div className="search-history-section">
          <div className="search-history-title">热门搜索</div>
          <Space wrap>
            {popularQueries.map((query, index) => (
              <Tag
                key={index}
                color="blue"
                style={{ cursor: 'pointer' }}
                onClick={() => handleHistoryClick(query)}
              >
                {query}
              </Tag>
            ))}
          </Space>
        </div>
      )}
      {searchHistory.length > 0 && (
        <div className="search-history-section">
          <div className="search-history-title">
            搜索历史
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={handleClearHistory}
              style={{ float: 'right' }}
            />
          </div>
          <List
            size="small"
            dataSource={searchHistory}
            renderItem={(item) => (
              <List.Item
                style={{ cursor: 'pointer', padding: '8px 12px' }}
                onClick={() => handleHistoryClick(item.query)}
              >
                <Space>
                  <HistoryOutlined />
                  <span>{item.query}</span>
                  <span style={{ color: '#999', fontSize: '12px' }}>
                    {item.resultCount} 条结果
                  </span>
                </Space>
              </List.Item>
            )}
          />
        </div>
      )}
      {searchHistory.length === 0 && popularQueries.length === 0 && (
        <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
          暂无搜索历史
        </div>
      )}
    </div>
  );

  return (
    <div ref={searchRef} className="search-bar-container">
      <Search
        placeholder={placeholder}
        value={searchValue}
        onChange={(e) => setSearchValue(e.target.value)}
        onSearch={handleSearch}
        onFocus={() => setHistoryVisible(true)}
        allowClear
        enterButton={<SearchOutlined />}
        size="large"
        style={{ width: '100%' }}
      />
      {showHistory && historyVisible && (
        <Dropdown
          open={historyVisible}
          overlay={historyMenu}
          placement="bottomLeft"
          trigger={['click']}
        >
          <div style={{ display: 'none' }} />
        </Dropdown>
      )}
    </div>
  );
}

export default SearchBar;
