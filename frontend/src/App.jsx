import { Routes, Route, Link, NavLink, useLocation, useNavigate } from 'react-router'
import './App.css'
import MovieListPage from './pages/MovieListPage'
import LoginPage from './pages/LoginPage'
import MovieDetailPage from './pages/MovieDetailPage'
import SeatPage from './pages/SeatPage'
import PaymentPage from './pages/PaymentPage'
import SignupPage from './pages/SignupPage'
import MyPage from './pages/MyPage'
import { clearTokens } from './auth'

function App() {
  useLocation()
  const navigate = useNavigate()
  const loggedIn = localStorage.getItem('accessToken') !== null

  async function handleLogout() {
    const token = localStorage.getItem('accessToken')
    try {
      await fetch('http://localhost:8082/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
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
