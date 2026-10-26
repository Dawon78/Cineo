import { Routes, Route, Link } from 'react-router'
import './App.css'
import MovieListPage from './pages/MovieListPage'
import LoginPage from './pages/LoginPage'

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
      </Routes>
    </div>
  )
}

export default App