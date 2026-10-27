import { Link } from 'react-router'

function MovieCard({ id, title, runtime, posterUrl, ageRating }) {
  return (
    <Link to={`/movies/${id}`} className="movie-card">
      <img className="movie-card-poster" src={posterUrl} alt={`${title} 포스터`} loading="lazy" />
      <h3 className="movie-card-title">{title}</h3>
      <div className="movie-card-meta">
        {ageRating && (
          <span className="age-badge">{/^\d+$/.test(ageRating) ? `${ageRating}세` : ageRating}</span>
        )}
        <span>{runtime}분</span>
      </div>
    </Link>
  )
}

export default MovieCard
