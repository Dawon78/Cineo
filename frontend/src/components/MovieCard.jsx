import { Link } from 'react-router'

function MovieCard({ id, title, runtime }) {
  return (
    <Link to={`/movies/${id}`}>
      <div>
        <h2>{title}</h2>
        <p>상영시간: {runtime}분</p>
      </div>
    </Link>
  )
}

export default MovieCard