import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router'
import MovieCard from '../components/MovieCard'
import { apiFetch } from '../api/client'

const SORT_OPTIONS = {
    default: { label: '기본순', compare: (a, b) => a.id - b.id },
    title: { label: '제목순', compare: (a, b) => a.title.localeCompare(b.title, 'ko') },
    // 개봉일이 없는 영화는 맨 뒤로 보낸다
    latest: { label: '최신 개봉순', compare: (a, b) => (b.releaseDate ?? '').localeCompare(a.releaseDate ?? '') },
    runtime: { label: '러닝타임 짧은순', compare: (a, b) => (a.runningTime ?? Infinity) - (b.runningTime ?? Infinity) },
}

// "다크나이트"로 찾아도 "다크 나이트"가 나오게 공백과 대소문자를 무시한다
function normalize(text) {
    return text.replace(/\s/g, '').toLowerCase()
}

function MovieListPage() {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 검색어와 정렬을 URL(?q=&sort=)에 두면 영화 상세에 갔다가 뒤로 와도 그대로 남는다
    const [searchParams, setSearchParams] = useSearchParams()
    // 입력창 값은 화면 상태로 따로 둔다. URL 값을 바로 쓰면 URL 반영이 한 박자 늦어서
    // 한글 조합 중에 옛 값으로 덮여 "ㅇ아안니" 처럼 깨진다
    const [query, setQuery] = useState(() => searchParams.get('q') ?? '')

    useEffect(() => {
        async function loadMovies() {
            try {
                const response = await apiFetch('/api/movies')
                if (!response.ok) {
                    throw new Error('영화 목록을 불러오지 못했어요')
                }
                const data = await response.json()
                setMovies(data)
            } catch (e) {
                setError(e.message)
            } finally {
                setLoading(false)
            }
        }
        loadMovies()
    }, [])

    const sortKey = SORT_OPTIONS[searchParams.get('sort')] ? searchParams.get('sort') : 'default'

    function updateParams(changes) {
        const next = new URLSearchParams(searchParams)
        for (const [key, value] of Object.entries(changes)) {
            // 기본값은 URL에서 빼서 주소를 깔끔하게 둔다
            if (!value || (key === 'sort' && value === 'default')) {
                next.delete(key)
            } else {
                next.set(key, value)
            }
        }
        // replace라서 한 글자씩 칠 때마다 방문 기록이 쌓이지 않는다
        setSearchParams(next, { replace: true })
    }

    function changeQuery(value) {
        setQuery(value)
        updateParams({ q: value })
    }

    const keyword = normalize(query)
    const visibleMovies = movies
        .filter(movie => normalize(movie.title).includes(keyword))
        .sort(SORT_OPTIONS[sortKey].compare)

    return (
        <section>
            <h1 className="page-title">상영 중인 영화</h1>
            {loading && <p className="notice">영화를 불러오는 중...</p>}
            {error && <p className="notice notice-error">{error}</p>}

            {!loading && !error && (
                <div className="movie-toolbar">
                    <input
                        type="search"
                        className="movie-search"
                        placeholder="영화 제목 검색"
                        aria-label="영화 제목 검색"
                        value={query}
                        onChange={e => changeQuery(e.target.value)}
                    />
                    <select
                        className="movie-sort"
                        aria-label="정렬"
                        value={sortKey}
                        onChange={e => updateParams({ sort: e.target.value })}
                    >
                        {Object.entries(SORT_OPTIONS).map(([key, option]) => (
                            <option key={key} value={key}>{option.label}</option>
                        ))}
                    </select>
                    <p className="movie-count" aria-live="polite">{visibleMovies.length}편</p>
                </div>
            )}

            {!loading && !error && visibleMovies.length === 0 && (
                <p className="notice">
                    '{query}'에 맞는 영화가 없어요.{' '}
                    <button type="button" className="link-button" onClick={() => changeQuery('')}>
                        검색어 지우기
                    </button>
                </p>
            )}

            <div className="movie-grid">
                {visibleMovies.map(movie => (
                    <MovieCard
                        key={movie.id}
                        id={movie.id}
                        title={movie.title}
                        runtime={movie.runningTime}
                        posterUrl={movie.posterUrl}
                        ageRating={movie.ageRating}
                    />
                ))}
            </div>
        </section>
    )
}

export default MovieListPage
