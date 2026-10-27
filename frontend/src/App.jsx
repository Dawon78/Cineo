import { Routes, Route, Link, NavLink, useLocation, useNavigate } from 'react-router'
import './App.css'
import MovieListPage from './pages/MovieListPage'
import LoginPage from './pages/LoginPage'
import MovieDetailPage from './pages/MovieDetailPage'
import SeatPage from './pages/SeatPage'
import PaymentPage from './pages/PaymentPage'
import SignupPage from './pages/SignupPage'
import MyPage from './pages/MyPage'
import { getAccessToken, clearTokens } from './auth'
import { apiFetch } from './api/client'

function App() {
  useLocation()
  const navigate = useNavigate()
  const loggedIn = getAccessToken() !== null

  async function handleLogout() {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } catch {
      // 서버가 꺼져 있어도 브라우저의 토큰은 지워서 로그아웃 상태로 만든다
    }
    clearTokens()
    navigate('/')
  }

  return (
    <>
      <header className="site-header">
        <Link to="/" className="logo">Cineo</Link>
        <nav className="site-nav">
          <NavLink to="/" end>영화</NavLink>
          {loggedIn ? (
            <>
              <NavLink to="/mypage">마이페이지</NavLink>
              <button type="button" className="nav-button" onClick={handleLogout}>로그아웃</button>
            </>
          ) : (
            <>
              <NavLink to="/login">로그인</NavLink>
              <NavLink to="/signup">회원가입</NavLink>
            </>
          )}
        </nav>
      </header>

      <main className="page">
        <Routes>
          <Route path="/" element={<MovieListPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/movies/:movieId" element={<MovieDetailPage />} />
          <Route path="/showtimes/:showtimeId/seats" element={<SeatPage />} />
          <Route path="/payment" element={<PaymentPage />} />
        </Routes>
      </main>
    </>
  )
}

export default App
