function MovieCard({ title, runtime }) {
  return (
    <div>
      <h2>{title}</h2>
      <p>상영시간: {runtime}분</p>
    </div>
  )
}

export default MovieCard