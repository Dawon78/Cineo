import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router'

function MovieDetailPage() {
    const { movieId } = useParams()
    const [movie, setMovie] = useState(null)
    const [showtimes, setShowtimes] = useState([])
    const [error, setError] = useState(null)

    useEffect(() => {
        async function load() {
            try {
                const movieRes = await fetch(`http://localhost:8082/api/movies/${movieId}`)
                if (!movieRes.ok) {
                    throw new Error(movieRes.status === 404 ? '영화를 찾을 수 없어요' : '영화 정보를 불러오지 못했어요')
                }
                setMovie(await movieRes.json())

                const showtimeRes = await fetch(`http://localhost:8082/api/movies/${movieId}/showtimes`)
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
        return <p>{error}</p>
    }

    if (movie === null) {
        return <p>불러오는 중...</p>
    }

    return (
        <div>
            <img src={movie.posterUrl} alt={movie.title} width={200} />
            <h2>{movie.title}</h2>
            <p>{movie.overView}</p>

            <h3>상영시간</h3>
            {showtimes.length === 0 && <p>상영 예정이 없어요</p>}
            {showtimes.map(showtime => (
                <div key={showtime.id}>
                    <Link to={`/showtimes/${showtime.id}/seats`}>
                        {new Date(showtime.startTime).toLocaleString('ko-KR', {
                            month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
                        })}
                        {' · '}{showtime.screenName} · {showtime.basePrice.toLocaleString()}원
                    </Link>
                </div>
            ))}
        </div>
    )
}

export default MovieDetailPage
