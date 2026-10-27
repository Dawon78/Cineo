import { useState, useEffect } from 'react'
import { apiFetch } from '../../api/client'

function AdminMoviesPage() {
    const [movies, setMovies] = useState(null)
    const [error, setError] = useState(null)
    const [reloadKey, setReloadKey] = useState(0)

    const [query, setQuery] = useState('')
    const [results, setResults] = useState(null)
    const [searching, setSearching] = useState(false)
    const [searchError, setSearchError] = useState(null)
    const [registeringId, setRegisteringId] = useState(null)

    useEffect(() => {
        async function load() {
            try {
                const response = await apiFetch('/api/admin/movies')
                if (!response.ok) {
                    setError('영화 목록을 불러오지 못했어요')
                    return
                }
                setMovies(await response.json())
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        load()
    }, [reloadKey])

    // 타이핑마다 TMDB를 부르지 않고, [검색] 버튼이나 Enter를 눌렀을 때만 검색한다
    async function handleSearch(e) {
        e.preventDefault()
        const keyword = query.trim()
        if (keyword === '') {
            return
        }
        setSearching(true)
        setSearchError(null)
        try {
            const response = await apiFetch(`/api/admin/movies/search?query=${encodeURIComponent(keyword)}`)
            if (!response.ok) {
                setSearchError(await response.text() || '검색하지 못했어요')
                return
            }
            setResults(await response.json())
        } catch {
            setSearchError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
        } finally {
            setSearching(false)
        }
    }

    async function handleRegister(result) {
        setRegisteringId(result.tmdbId)
        let response
        try {
            response = await apiFetch('/api/admin/movies', {
                method: 'POST',
                body: { tmdbId: result.tmdbId },
            })
        } catch {
            window.alert('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            return
        } finally {
            setRegisteringId(null)
        }
        // 409는 다른 탭에서 이미 등록한 경우라 "등록됨"으로 바꿔 준다
        if (!response.ok && response.status !== 409) {
            window.alert(await response.text())
            return
        }
        setResults(list => list.map(item => item.tmdbId === result.tmdbId ? { ...item, registered: true } : item))
        setReloadKey(key => key + 1)
    }

    return (
        <>
            <h2 className="section-title">TMDB에서 영화 찾기</h2>
            <form className="admin-search" onSubmit={handleSearch}>
                <input
                    type="search"
                    className="movie-search"
                    placeholder="영화 제목"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                />
                <button type="submit" className="btn btn-primary" disabled={searching || query.trim() === ''}>
                    {searching ? '검색하는 중...' : '검색'}
                </button>
            </form>
            {searchError && <p className="notice notice-error">{searchError}</p>}
            {results !== null && results.length === 0 && <p className="notice">검색 결과가 없어요.</p>}
            {results !== null && results.length > 0 && (
                <ul className="admin-search-results">
                    {results.map(result => (
                        <li key={result.tmdbId} className="admin-search-card">
                            {result.posterUrl
                                ? <img src={result.posterUrl} alt="" loading="lazy" />
                                : <div className="admin-poster-empty">포스터 없음</div>}
                            <p className="admin-search-title">{result.title}</p>
                            <p className="admin-muted">{result.releaseDate || '개봉일 미정'}</p>
                            <button
                                type="button"
                                className="btn"
                                onClick={() => handleRegister(result)}
                                disabled={result.registered || registeringId === result.tmdbId}
                            >
                                {result.registered ? '등록됨' : registeringId === result.tmdbId ? '등록하는 중...' : '등록'}
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            <h2 className="section-title">등록된 영화 {movies && <small className="admin-muted">{movies.length}편</small>}</h2>
            {error && <p className="notice notice-error">{error}</p>}
            {!error && movies === null && <p className="notice">불러오는 중...</p>}
            {movies !== null && (
                <div className="admin-table-wrap">
                    <table className="admin-table">
                        <thead>
                            <tr>
                                <th></th>
                                <th>제목</th>
                                <th>개봉일</th>
                                <th>러닝타임</th>
                                <th>관람 등급</th>
                            </tr>
                        </thead>
                        <tbody>
                            {movies.map(movie => (
                                <tr key={movie.id}>
                                    <td>
                                        {movie.posterUrl && <img className="admin-thumb" src={movie.posterUrl} alt="" loading="lazy" />}
                                    </td>
                                    <td>{movie.title}</td>
                                    <td>{movie.releaseDate ?? '-'}</td>
                                    <td>
                                        {movie.runningTime
                                            ? `${movie.runningTime}분`
                                            : <span className="admin-warning">러닝타임 없음</span>}
                                    </td>
                                    <td>{movie.ageRating}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </>
    )
}

export default AdminMoviesPage
