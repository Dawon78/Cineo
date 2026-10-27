import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router'
import { apiFetch } from '../api/client'

function formatTime(dateTime) {
    return new Date(dateTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function formatDay(date) {
    return new Date(`${date}T00:00:00`).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
}

function MovieDetailPage() {
    const { movieId } = useParams()
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

    const days = [...new Set(showtimes.map(showtime => showtime.startTime.slice(0, 10)))]

    return (
        <article className="movie-detail">
            <img className="movie-detail-poster" src={movie.posterUrl} alt={`${movie.title} 포스터`} />
            <div>
                <h1 className="movie-detail-title">{movie.title}</h1>
                <p className="movie-overview">{movie.overview}</p>

                <h2 className="section-title">상영시간 선택</h2>
                {showtimes.length === 0 && <p className="notice">상영 예정이 없어요. 다른 영화를 골라 주세요.</p>}
                {days.map(day => (
                    <div key={day} className="showtime-day">
                        <p className="showtime-day-label">{formatDay(day)}</p>
                        <div className="showtime-list">
                            {showtimes
                                .filter(showtime => showtime.startTime.startsWith(day))
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
                    </div>
                ))}
            </div>
        </article>
    )
}

export default MovieDetailPage
