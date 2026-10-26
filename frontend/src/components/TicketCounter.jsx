import { useState, useEffect } from 'react'

function TicketCounter() {
  const [count, setCount] = useState(0)
  useEffect(() => {
    console.log('처음 한 번만 실행')
  }, [])

  useEffect(() => {
    console.log('인원이 바뀜:', count)
  }, [count])

  return (
    <div>
      <p>인원: {count}명</p>
      <button onClick={() => {
        if (count > 0) {
          setCount(count - 1)
        }

      }}>-</button>
      <button onClick={() =>
        setCount(count + 1)}
        disabled={count == 0}
      >+</button>
    </div>
  )
}

export default TicketCounter

