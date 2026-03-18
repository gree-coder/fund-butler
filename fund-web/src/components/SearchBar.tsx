import React, { useState, useCallback, useRef, useEffect } from 'react';
import { AutoComplete, Input, Spin, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { fundApi, type FundSearchItem } from '../api/fund';
import { formatFundType } from '../utils/format';

const { Text } = Typography;

const SearchBar: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<FundSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const navigate = useNavigate();
  const location = useLocation();

  // Sync keyword from URL when navigating to /search?q=xxx
  useEffect(() => {
    if (location.pathname === '/search') {
      const q = new URLSearchParams(location.search).get('q') || '';
      if (q) setKeyword(q);
    }
  }, [location.pathname, location.search]);

  const doSearch = useCallback(async (value: string) => {
    if (value.length < 2) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      const res = await fundApi.search(value);
      setResults(res.list || []);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleSearch = (value: string) => {
    setKeyword(value);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => doSearch(value), 300);
  };

  const handleSelect = (value: string) => {
    setKeyword('');
    setResults([]);
    navigate(`/fund/${value}`);
  };

  const handlePressEnter = () => {
    if (keyword.trim()) {
      setResults([]);
      navigate(`/search?q=${encodeURIComponent(keyword.trim())}`);
    }
  };

  useEffect(() => {
    return () => clearTimeout(timerRef.current);
  }, []);

  const options = results.slice(0, 10).map((item) => ({
    value: item.code,
    label: (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Text strong>{item.code}</Text>
        <Text>{item.name}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatFundType(item.type)}
        </Text>
      </div>
    ),
  }));

  return (
    <AutoComplete
      style={{ width: 320 }}
      options={options}
      onSearch={handleSearch}
      onSelect={handleSelect}
      value={keyword}
    >
      <Input
        prefix={<SearchOutlined />}
        placeholder="搜索基金名称或代码"
        onPressEnter={handlePressEnter}
        allowClear
        suffix={loading ? <Spin size="small" /> : null}
      />
    </AutoComplete>
  );
};

export default SearchBar;
