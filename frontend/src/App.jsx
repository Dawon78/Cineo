import { Routes, Route, Link } from 'react-router'
import './App.css'
import MovieListPage from './pages/MovieListPage'
import LoginPage from './pages/LoginPage'
import MovieDetailPage from './pages/MovieDetailPage'
import SeatPage from './pages/SeatPage'

function App() {
  return (
    <div>
      <header>
        <h1>Cineo</h1>
        <nav>
          <Link to="/">영화</Link> | <Link to="/login">로그인</Link>
        </nav>
      </header>

      <Routes>
        <Route path="/" element={<MovieListPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/movies/:movieId" element={<MovieDetailPage />} />
        <Route path="/showtimes/:showtimeId/seats" element={<SeatPage />} />
      </Routes>
    </div>
  )
}

export default App