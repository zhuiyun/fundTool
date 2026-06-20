#!/usr/bin/env node
/**
 * 测试 Yahoo Finance 热门美股接口
 * 用法: node scripts/fetch-hot-stocks.js
 */

const https = require('https');

const ENDPOINTS = [
  {
    name: '涨幅榜 (day_gainers)',
    url: 'https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved?scrIds=day_gainers&count=10',
  },
  {
    name: '跌幅榜 (day_losers)',
    url: 'https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved?scrIds=day_losers&count=10',
  },
  {
    name: '成交量活跃 (most_actives)',
    url: 'https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved?scrIds=most_actives&count=10',
  },
  {
    name: '热门搜索 (trending US)',
    url: 'https://query1.finance.yahoo.com/v1/finance/trending/US?count=20',
  },
];

function get(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Accept': 'application/json',
      },
    }, (res) => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(Buffer.concat(chunks).toString()) });
        } catch (e) {
          reject(new Error(`JSON parse failed: ${e.message}`));
        }
      });
    });
    req.on('error', reject);
    req.setTimeout(8000, () => { req.destroy(); reject(new Error('timeout')); });
  });
}

function formatQuote(q) {
  const chg = q.regularMarketChangePercent?.toFixed(2);
  const sign = chg >= 0 ? '+' : '';
  return `  ${(q.symbol || '').padEnd(8)} $${(q.regularMarketPrice || 0).toFixed(2).padStart(8)}  ${sign}${chg}%  成交量: ${(q.regularMarketVolume || 0).toLocaleString()}`;
}

async function main() {
  for (const ep of ENDPOINTS) {
    console.log(`\n━━ ${ep.name} ━━`);
    try {
      const { status, body } = await get(ep.url);
      if (status !== 200) { console.log(`  HTTP ${status}`); continue; }

      // screener 结构
      const quotes = body?.finance?.result?.[0]?.quotes;
      if (quotes) {
        quotes.forEach(q => console.log(formatQuote(q)));
        continue;
      }

      // trending 结构（quotes 只含 symbol，无价格）
      const trendingQuotes = body?.finance?.result?.[0]?.quotes;
      if (trendingQuotes && trendingQuotes[0]?.symbol && !trendingQuotes[0]?.regularMarketPrice) {
        console.log('  ' + trendingQuotes.map(q => q.symbol).join(', '));
        continue;
      }

      console.log('  (响应结构未识别)');
      console.log(JSON.stringify(body, null, 2).slice(0, 300));
    } catch (err) {
      console.log(`  错误: ${err.message}`);
    }
  }
}

main();
