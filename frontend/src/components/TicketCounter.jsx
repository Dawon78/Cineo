import { useState } from 'react'

function TicketCounter() {
  const [count, setCount] = useState(0)

  return (
    <div>
      <p>인원: {count}명</p>
      <button onClick={() => setCount(count - 1)} disabled={count === 0}>-</button>
      <button onClick={() => setCount(count + 1)}>+</button>
    </div>
  )
}

export default TicketCounter
