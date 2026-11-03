import { useState, useEffect } from 'react'
import { useParams, useSearchParams, Link } from 'react-router'
import { apiFetch } from '../api/client'

// 실제 영화관처럼 오늘부터 2주 치 날짜만 탭으로 보여준다. 상영이 더 먼 날까지 등록돼 있어도 탭이 끝없이 늘어나지 않는다
const DATE_TAB_DAYS = 14

function formatTime(dateTime) {
    return new Date(dateTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

// toISOString()은 UTC 기준이라 한국 시간 오전 9시 전에는 어제 날짜가 나온다. 로컬 날짜로 직접 만든다
function toLocalDateKey(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${date.getFullYear()}-${month}-${day}`
}

function relativeDayLabel(day) {
    const today = new Date()
    const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1)
    if (day === toLocalDateKey(today)) {
        return '오늘'
    }
    if (day === toLocalDateKey(tomorrow)) {
        return '내일'
    }
    return null
}

function lastTabDay() {
    const today = new Date()
    return toLocalDateKey(new Date(today.getFullYear(), today.getMonth(), today.getDate() + DATE_TAB_DAYS - 1))
}

function formatTabDate(day) {
    const date = new Date(`${day}T00:00:00`)
    const weekday = date.toLocaleDateString('ko-KR', { weekday: 'short' })
    return `${date.getMonth() + 1}.${date.getDate()} (${weekday})`
}

function MovieDetailPage() {
    const { movieId } = useParams()
    // 고른 날짜를 URL(?date=)에 두면 좌석 화면에 갔다가 뒤로 와도 보던 탭이 그대로 남는다
    const [searchParams, setSearchParams] = useSearchParams()
    const [movie, setMovie] = useState(null)
    const [showtimes, setShowtimes] = useState([])
    const [error, setError] = useState(null)

    useEffect(() => {
        async function load() {
            try {
                const movieRes = await apiFetch(`/api/movies/${movieId}`)
                if (!movieRes.ok) {
                    throw new Error(movieRes.status === 404 ? '영화를 찾을 수 없어요' : '영화 정보를 불러오지 못했어요')
                }
                setMovie(await movieRes.json())

                const showtimeRes = await apiFetch(`/api/movies/${movieId}/showtimes`)
                if (!showtimeRes.ok) {
                    throw new Error('상영시간을 불러오지 못했어요')
                }
                setShowtimes(await showtimeRes.json())
            } catch (e) {
                setError(e.message)
            }
        }
        load()
    }, [movieId])

    if (error) {
        return <p className="notice notice-error">{error}</p>
    }

    if (movie === null) {
        return <p className="notice">불러오는 중...</p>
    }

    // 날짜 문자열(YYYY-MM-DD)은 글자 순서가 곧 날짜 순서라 문자열 비교로 자를 수 있다
    const lastDay = lastTabDay()
    const days = [...new Set(showtimes.map(showtime => showtime.startTime.slice(0, 10)))].filter(day => day <= lastDay)
    // URL에 날짜가 없거나 상영이 없는 날이면 첫날을 보여준다
    const requestedDay = searchParams.get('date')
    const selectedDay = days.includes(requestedDay) ? requestedDay : days[0]

    function selectDay(day) {
        // replace라서 탭을 여러 번 눌러도 뒤로 가기 한 번이면 영화 목록으로 간다
        setSearchParams({ date: day }, { replace: true })
    }

    return (
        <article className="movie-detail">
            <img className="movie-detail-poster" src={movie.posterUrl} alt={`${movie.title} 포스터`} />
            <div>
                <h1 className="movie-detail-title">{movie.title}</h1>
                <p className="movie-overview">{movie.overview}</p>

                <h2 className="section-title">상영시간 선택</h2>
                {days.length === 0 && <p className="notice">상영 예정이 없어요. 다른 영화를 골라 주세요.</p>}
                {days.length > 0 && (
                    <>
                        <div className="date-tabs" role="tablist" aria-label="상영 날짜">
                            {days.map(day => {
                                const relative = relativeDayLabel(day)
                                const count = showtimes.filter(showtime => showtime.startTime.startsWith(day)).length
                                const selected = day === selectedDay
                                return (
                                    <button
                                        key={day}
                                        type="button"
                                        role="tab"
                                        id={`date-tab-${day}`}
                                        aria-selected={selected}
                                        aria-controls="showtime-panel"
                                        className={selected ? 'date-tab date-tab-active' : 'date-tab'}
                                        onClick={() => selectDay(day)}
                                    >
                                        {relative && <span className="date-tab-relative">{relative}</span>}
                                        <span className="date-tab-date">{formatTabDate(day)}</span>
                                        <span className="date-tab-count">{count}회</span>
                                    </button>
                                )
                            })}
                        </div>
                        <div
                            id="showtime-panel"
                            role="tabpanel"
                            aria-labelledby={`date-tab-${selectedDay}`}
                            className="showtime-list"
                        >
                            {showtimes
                                .filter(showtime => showtime.startTime.startsWith(selectedDay))
                                .map(showtime => (
                                    <Link
                                        key={showtime.id}
                                        to={`/showtimes/${showtime.id}/seats`}
                                        className="showtime-chip"
                                    >
                                        <span className="showtime-time">{formatTime(showtime.startTime)}</span>
                                        <span className="showtime-sub">
                                            ~{formatTime(showtime.endTime)} {showtime.screenName}
                                        </span>
                                        <span className="showtime-sub">{showtime.basePrice.toLocaleString()}원</span>
                                    </Link>
                                ))}
                        </div>
                    </>
                )}
            </div>
        </article>
    )
}

export default MovieDetailPage
