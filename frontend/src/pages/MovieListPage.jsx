import { useState, useEffect } from 'react'
import MovieCard from '../components/MovieCard'

function MovieListPage() {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        async function loadMovies() {
            try {
                const response = await fetch('http://localhost:8082/api/movies')
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

    return (
        <div>
            <h2>상영 중인 영화</h2>
            {loading && <p>영화를 불러오는 중...</p>}
            {error && <p>{error}</p>}
            {movies.map(movie => (
                <MovieCard key={movie.id} id={movie.id} title={movie.title} runtime={movie.runningTime} />
            ))}
        </div>
    )
}

export default MovieListPage