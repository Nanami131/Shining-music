#!/usr/bin/env python3
"""
基于 MEmoLon 词库分析歌词情绪。
用法: python3 analyze_mood.py <中文歌词文本>
输出: JSON 格式的 8 维情绪评分 (归一化到 0-1)
"""
import sys
import json
import csv
import re
import jieba

LEXICON_PATH = "docs/emotion-lexicons/zh.tsv"
DIMS = ["valence", "arousal", "dominance", "joy", "anger", "sadness", "fear", "disgust"]

# MEmoLon 原始值范围 1-9，中性 = 5
SCALE_MIN = 1.0
SCALE_MAX = 9.0

def load_lexicon(path):
    lexicon = {}
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            word = row["word"]
            try:
                scores = {d: float(row[d]) for d in DIMS}
                lexicon[word] = scores
            except (ValueError, KeyError):
                continue
    return lexicon

def strip_timestamps(text):
    return re.sub(r'\[\d{2}:\d{2}\.\d{2,3}\]', '', text)

def normalize(val, dim):
    """将 MEmoLon 1-9 值归一化到 0-1"""
    return max(0.0, min(1.0, (val - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)))

def analyze(lyrics_text, lexicon):
    clean = strip_timestamps(lyrics_text).replace("\\n", "\n")
    words = list(jieba.cut(clean))
    
    hit_count = 0
    totals = {d: 0.0 for d in DIMS}
    matched_words = {}
    
    for w in words:
        w = w.strip()
        if not w or len(w) < 2:
            continue
        if w in lexicon:
            hit_count += 1
            for d in DIMS:
                totals[d] += lexicon[w][d]
            if w not in matched_words:
                matched_words[w] = lexicon[w]
    
    if hit_count == 0:
        return {d: 0.5 for d in DIMS}, 0, {}
    
    averages = {}
    for d in DIMS:
        raw_avg = totals[d] / hit_count
        averages[d] = round(normalize(raw_avg, d), 3)
    
    return averages, hit_count, matched_words

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_mood.py '<lyrics_text>'", file=sys.stderr)
        sys.exit(1)
    
    lyrics = sys.argv[1]
    
    print("加载 MEmoLon 词库...", file=sys.stderr)
    lexicon = load_lexicon(LEXICON_PATH)
    print(f"词库加载完成: {len(lexicon)} 词条", file=sys.stderr)
    
    scores, hits, matched = analyze(lyrics, lexicon)
    
    top_emotional = sorted(matched.items(), key=lambda x: abs(x[1]["valence"] - 5), reverse=True)[:15]
    
    result = {
        "scores": scores,
        "hit_count": hits,
        "total_words": len(list(jieba.cut(strip_timestamps(lyrics).replace("\\n", "\n")))),
        "top_emotional_words": [
            {"word": w, **{d: round(s[d], 2) for d, s in [(dd, sc) for dd, sc in zip(DIMS, [scores_dict]*len(DIMS))]}}
            for w, scores_dict in top_emotional[:10]
        ] if top_emotional else [],
    }
    
    # 简化输出：只输出分数和匹配统计
    output = {
        "scores": scores,
        "hit_count": hits,
        "hit_rate": round(hits / max(1, result["total_words"]) * 100, 1),
        "top_words": [w for w, _ in top_emotional[:10]]
    }
    
    print(json.dumps(output, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
