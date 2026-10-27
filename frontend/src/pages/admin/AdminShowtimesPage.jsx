import { useState, useEffect } from 'react'
import { apiFetch } from '../../api/client'

const FORMATS = [
    { value: 'TWO_D', label: '2D' },
    { value: 'THREE_D', label: '3D' },
    { value: 'IMAX', label: 'IMAX' },
    { value: 'FOUR_DX', label: '4DX' },
]

function formatLabel(value) {
    return FORMATS.find(format => format.value === value)?.label ?? value
}

function formatTime(date) {
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function formatDate(date) {
    return date.toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric', weekday: 'short' })
}

// toISOString()은 UTC 기준이라 날짜가 하루 밀릴 수 있다. 로컬 날짜로 직접 만든다
function toLocalDateKey(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${date.getFullYear()}-${month}-${day}`
}

function AdminShowtimesPage() {
    const [movies, setMovies] = useState([])
    const [screens, setScreens] = useState([])
    const [showtimes, setShowtimes] = useState(null)
    const [error, setError] = useState(null)
    const [reloadKey, setReloadKey] = useState(0)

    const [movieId, setMovieId] = useState('')
    const [screenId, setScreenId] = useState('')
    const [format, setFormat] = useState('TWO_D')
    const [startTime, setStartTime] = useState('')
    const [basePrice, setBasePrice] = useState('12000')
    const [submitting, setSubmitting] = useState(false)
    const [message, setMessage] = useState(null)

    const [filterMovieId, setFilterMovieId] = useState('')
    const [filterDate, setFilterDate] = useState('')
    const [deletingId, setDeletingId] = useState(null)

    useEffect(() => {
        async function load() {
            try {
                const [movieRes, screenRes, showtimeRes] = await Promise.all([
                    apiFetch('/api/admin/movies'),
                    apiFetch('/api/admin/screens'),
                    apiFetch('/api/admin/showtimes'),
                ])
                if (!movieRes.ok || !screenRes.ok || !showtimeRes.ok) {
                    setError('상영시간 정보를 불러오지 못했어요')
                    return
                }
                setMovies(await movieRes.json())
                setScreens(await screenRes.json())
                const list = await showtimeRes.json()
                setShowtimes([...list].sort((a, b) => a.startTime.localeCompare(b.startTime)))
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        load()
    }, [reloadKey])

    const selectedMovie = movies.find(movie => String(movie.id) === movieId)
    const noRunningTime = selectedMovie && !selectedMovie.runningTime
    // 러닝타임으로 끝나는 시각을 미리 보여준다. 서버도 같은 방식(시작 + 러닝타임)으로 계산한다
    const endPreview = selectedMovie?.runningTime && startTime
        ? new Date(new Date(startTime).getTime() + selectedMovie.runningTime * 60 * 1000)
        : null

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setMessage(null)
        let response
        try {
            response = await apiFetch('/api/admin/showtimes', {
                method: 'POST',
                body: {
                    movieId: Number(movieId),
                    screenId: Number(screenId),
                    format,
                    startTime,
                    basePrice: Number(basePrice),
                },
            })
        } catch {
            setMessage({ error: true, text: '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.' })
            return
        } finally {
            setSubmitting(false)
        }
        if (!response.ok) {
            setMessage({ error: true, text: await response.text() })
            return
        }
        setMessage({ error: false, text: '상영시간을 등록했어요.' })
        setStartTime('')
        setReloadKey(key => key + 1)
    }

    async function handleDelete(showtime) {
        const when = `${formatDate(new Date(showtime.startTime))} ${formatTime(new Date(showtime.startTime))}`
        if (!window.confirm(`${when} ${showtime.movieTitle} (${showtime.screenName}) 상영을 삭제할까요?`)) {
            return
        }
        setDeletingId(showtime.id)
        let response
        try {
            response = await apiFetch(`/api/admin/showtimes/${showtime.id}`, { method: 'DELETE' })
        } catch {
            window.alert('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            return
        } finally {
            setDeletingId(null)
        }
        // 삭제할 수 있는지는 서버 한 곳에서만 판단한다. 409면 서버가 준 이유를 그대로 보여준다
        if (!response.ok) {
            window.alert(await response.text())
            return
        }
        setReloadKey(key => key + 1)
    }

    const filtered = (showtimes ?? []).filter(showtime =>
        (filterMovieId === '' || String(showtime.movieId) === filterMovieId)
        && (filterDate === '' || toLocalDateKey(new Date(showtime.startTime)) === filterDate))

    return (
        <>
            <h2 className="section-title">상영시간 등록</h2>
            <form className="admin-form admin-form-wide" onSubmit={handleSubmit}>
                <div className="admin-form-row">
                    <label className="field">
                        <span>영화</span>
                        <select required value={movieId} onChange={(e) => setMovieId(e.target.value)}>
                            <option value="">선택</option>
                            {movies.map(movie => <option key={movie.id} value={movie.id}>{movie.title}</option>)}
                        </select>
                    </label>
                    <label className="field">
                        <span>상영관</span>
                        <select required value={screenId} onChange={(e) => setScreenId(e.target.value)}>
                            <option value="">선택</option>
                            {screens.map(screen => <option key={screen.id} value={screen.id}>{screen.name}</option>)}
                        </select>
                    </label>
                    <label className="field">
                        <span>포맷</span>
                        <select value={format} onChange={(e) => setFormat(e.target.value)}>
                            {FORMATS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
                        </select>
                    </label>
                </div>
                <div className="admin-form-row">
                    <label className="field">
                        <span>시작 일시 {endPreview && <small>종료 예정 {formatTime(endPreview)}</small>}</span>
                        <input
                            type="datetime-local"
                            required
                            value={startTime}
                            onChange={(e) => setStartTime(e.target.value)}
                        />
                    </label>
                    <label className="field">
                        <span>기본 가격 (원)</span>
                        <input
                            type="number"
                            required
                            min={1}
                            value={basePrice}
                            onChange={(e) => setBasePrice(e.target.value)}
                        />
                    </label>
                </div>
                {noRunningTime && (
                    <p className="notice notice-error">러닝타임 정보가 없는 영화라 상영시간을 등록할 수 없어요.</p>
                )}
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? '등록하는 중...' : '등록'}
                </button>
                {message && <p className={message.error ? 'auth-message is-error' : 'auth-message'}>{message.text}</p>}
            </form>

            <h2 className="section-title">상영시간 목록 {showtimes && <small className="admin-muted">{filtered.length}건</small>}</h2>
            <div className="movie-toolbar">
                <select className="movie-sort" value={filterMovieId} onChange={(e) => setFilterMovieId(e.target.value)}>
                    <option value="">전체 영화</option>
                    {movies.map(movie => <option key={movie.id} value={movie.id}>{movie.title}</option>)}
                </select>
                <input
                    type="date"
                    className="movie-sort"
                    value={filterDate}
                    onChange={(e) => setFilterDate(e.target.value)}
                />
                {(filterMovieId !== '' || filterDate !== '') && (
                    <button type="button" className="link-button" onClick={() => { setFilterMovieId(''); setFilterDate('') }}>
                        필터 지우기
                    </button>
                )}
            </div>
            {error && <p className="notice notice-error">{error}</p>}
            {!error && showtimes === null && <p className="notice">불러오는 중...</p>}
            {showtimes !== null && filtered.length === 0 && <p className="notice">조건에 맞는 상영시간이 없어요.</p>}
            {filtered.length > 0 && (
                <div className="admin-table-wrap">
                    <table className="admin-table">
                        <thead>
                            <tr>
                                <th>날짜</th>
                                <th>시간</th>
                                <th>영화</th>
                                <th>상영관</th>
                                <th>포맷</th>
                                <th>기본 가격</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map(showtime => {
                                const start = new Date(showtime.startTime)
                                const end = new Date(showtime.endTime)
                                return (
                                    <tr key={showtime.id}>
                                        <td>{formatDate(start)}</td>
                                        <td>{formatTime(start)}~{formatTime(end)}</td>
                                        <td>{showtime.movieTitle}</td>
                                        <td>{showtime.screenName}</td>
                                        <td>{formatLabel(showtime.format)}</td>
                                        <td>{showtime.basePrice.toLocaleString()}원</td>
                                        <td>
                                            <button
                                                type="button"
                                                className="btn btn-small"
                                                onClick={() => handleDelete(showtime)}
                                                disabled={deletingId === showtime.id}
                                            >
                                                {deletingId === showtime.id ? '삭제하는 중...' : '삭제'}
                                            </button>
                                        </td>
                                    </tr>
                                )
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </>
    )
}

export default AdminShowtimesPage
