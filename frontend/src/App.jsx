import { useEffect, useState } from 'react';

const CONFIG = {
  memberService: 'http://localhost:8081',
  catalogService: 'http://localhost:8082',
  borrowingService: 'http://localhost:8083',
  fineService: 'http://localhost:8084',
  notificationService: 'http://localhost:8085'
};

async function apiRequest(method, url, body) {
  const options = { method, headers: {} };

  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, options);
  } catch (networkError) {
    throw new Error(`Could not reach ${url}. Is that service running?`);
  }

  const text = await response.text();
  let data = null;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      // Non-JSON body; response.ok still decides success.
    }
  }

  if (!response.ok) {
    let message = `Request failed (HTTP ${response.status})`;

    if (data) {
      if (typeof data.error === 'string') {
        message = data.error;
      } else {
        const firstField = Object.values(data).find((value) => typeof value === 'string');
        if (firstField) message = firstField;
      }
    }

    throw new Error(message);
  }

  return data;
}

const api = {
  get: (url) => apiRequest('GET', url),
  post: (url, body) => apiRequest('POST', url, body),
  put: (url, body) => apiRequest('PUT', url, body)
};

function fmtDate(value) {
  return value || '—';
}

function tagFor(text, variant) {
  return <span className={`tag tag-${variant}`}>{text}</span>;
}

function App() {
  const [activeView, setActiveView] = useState('dashboard');
  const [dashboardStats, setDashboardStats] = useState(null);

  const [members, setMembers] = useState([]);
  const [bookList, setBookList] = useState([]);
  const [borrowings, setBorrowings] = useState([]);
  const [fines, setFines] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [memberOptions, setMemberOptions] = useState([]);
  const [bookOptions, setBookOptions] = useState([]);

  const [memberMsg, setMemberMsg] = useState({ kind: '', text: '' });
  const [bookMsg, setBookMsg] = useState({ kind: '', text: '' });
  const [borrowMsg, setBorrowMsg] = useState({ kind: '', text: '' });
  const [returnsMsg, setReturnsMsg] = useState({ kind: '', text: '' });
  const [finesMsg, setFinesMsg] = useState({ kind: '', text: '' });

  const [memberForm, setMemberForm] = useState({ name: '', email: '', phone: '' });
  const [bookForm, setBookForm] = useState({ title: '', author: '', isbn: '', totalCopies: '1' });
  const [borrowForm, setBorrowForm] = useState({ memberId: '', bookId: '' });
  const [finesLookup, setFinesLookup] = useState('');
  const [notificationsLookup, setNotificationsLookup] = useState('');

  const [loadingStates, setLoadingStates] = useState({
    dashboard: false,
    members: false,
    books: false,
    borrow: false,
    returns: false,
    fines: false,
    notifications: false
  });

  async function loadDashboard() {
    setLoadingStates((prev) => ({ ...prev, dashboard: true }));

    try {
      const results = await Promise.allSettled([
        api.get(`${CONFIG.memberService}/members`),
        api.get(`${CONFIG.catalogService}/books`),
        api.get(`${CONFIG.borrowingService}/borrowings`),
        api.get(`${CONFIG.fineService}/fines`),
        api.get(`${CONFIG.notificationService}/notifications`)
      ]);

      const [membersResult, booksResult, borrowingsResult, finesResult, notificationsResult] = results;

      const stats = {
        members: membersResult.status === 'fulfilled' ? membersResult.value.length : 0,
        books: booksResult.status === 'fulfilled' ? booksResult.value.length : 0,
        activeBorrowings: borrowingsResult.status === 'fulfilled' ? borrowingsResult.value.filter((b) => b.status === 'BORROWED').length : 0,
        unpaidFines: finesResult.status === 'fulfilled' ? finesResult.value.filter((f) => f.status === 'UNPAID').length : 0,
        unreadNotifications: notificationsResult.status === 'fulfilled' ? notificationsResult.value.filter((n) => !n.readStatus).length : 0
      };

      setDashboardStats(stats);
    } catch (error) {
      setDashboardStats({
        members: 0,
        books: 0,
        activeBorrowings: 0,
        unpaidFines: 0,
        unreadNotifications: 0
      });
    } finally {
      setLoadingStates((prev) => ({ ...prev, dashboard: false }));
    }
  }

  async function loadMembers() {
    setLoadingStates((prev) => ({ ...prev, members: true }));
    try {
      const data = await api.get(`${CONFIG.memberService}/members`);
      setMembers(data);
    } catch (error) {
      setMembers([]);
    } finally {
      setLoadingStates((prev) => ({ ...prev, members: false }));
    }
  }

  async function loadBooks() {
    setLoadingStates((prev) => ({ ...prev, books: true }));
    try {
      const data = await api.get(`${CONFIG.catalogService}/books`);
      setBookList(data);
      setBookOptions(data);
    } catch (error) {
      setBookList([]);
      setBookOptions([]);
    } finally {
      setLoadingStates((prev) => ({ ...prev, books: false }));
    }
  }

  async function loadBorrowView() {
    setLoadingStates((prev) => ({ ...prev, borrow: true }));
    try {
      const [membersData, booksData] = await Promise.all([
        api.get(`${CONFIG.memberService}/members`),
        api.get(`${CONFIG.catalogService}/books`)
      ]);

      setMemberOptions(membersData);
      setBookOptions(booksData);
    } catch (error) {
      setMemberOptions([]);
      setBookOptions([]);
      setBorrowMsg({ kind: 'error', text: `Couldn't load members/books: ${error.message}` });
    } finally {
      setLoadingStates((prev) => ({ ...prev, borrow: false }));
    }
  }

  async function loadReturns() {
    setLoadingStates((prev) => ({ ...prev, returns: true }));
    try {
      const data = await api.get(`${CONFIG.borrowingService}/borrowings`);
      setBorrowings(data.filter((item) => item.status === 'BORROWED'));
    } catch (error) {
      setBorrowings([]);
      setReturnsMsg({ kind: 'error', text: `Couldn't load active borrowings: ${error.message}` });
    } finally {
      setLoadingStates((prev) => ({ ...prev, returns: false }));
    }
  }

  async function loadFines(memberId = '') {
    setLoadingStates((prev) => ({ ...prev, fines: true }));
    try {
      const data = await api.get(
        memberId
          ? `${CONFIG.fineService}/fines?memberId=${memberId}`
          : `${CONFIG.fineService}/fines`
      );
      setFines(data);
      setFinesMsg({ kind: '', text: '' });
    } catch (error) {
      setFines([]);
      setFinesMsg({ kind: 'error', text: error.message });
    } finally {
      setLoadingStates((prev) => ({ ...prev, fines: false }));
    }
  }

  async function loadNotifications(memberId = '') {
    setLoadingStates((prev) => ({ ...prev, notifications: true }));
    try {
      const data = await api.get(
        memberId
          ? `${CONFIG.notificationService}/notifications?memberId=${memberId}`
          : `${CONFIG.notificationService}/notifications`
      );
      setNotifications(data);
    } catch (error) {
      setNotifications([]);
    } finally {
      setLoadingStates((prev) => ({ ...prev, notifications: false }));
    }
  }

  useEffect(() => {
    if (activeView === 'dashboard') loadDashboard();
    if (activeView === 'members') loadMembers();
    if (activeView === 'books') loadBooks();
    if (activeView === 'borrow') loadBorrowView();
    if (activeView === 'returns') loadReturns();
    if (activeView === 'fines') loadFines();
    if (activeView === 'notifications') loadNotifications();
  }, [activeView]);

  async function handleMemberSubmit(event) {
    event.preventDefault();
    setMemberMsg({ kind: '', text: '' });

    try {
      await api.post(`${CONFIG.memberService}/members`, {
        name: memberForm.name.trim(),
        email: memberForm.email.trim(),
        phone: memberForm.phone.trim() || null
      });

      setMemberMsg({ kind: 'success', text: 'Member added.' });
      setMemberForm({ name: '', email: '', phone: '' });
      loadMembers();
    } catch (error) {
      setMemberMsg({ kind: 'error', text: error.message });
    }
  }

  async function handleBookSubmit(event) {
    event.preventDefault();
    setBookMsg({ kind: '', text: '' });

    try {
      await api.post(`${CONFIG.catalogService}/books`, {
        title: bookForm.title.trim(),
        author: bookForm.author.trim(),
        isbn: bookForm.isbn.trim(),
        totalCopies: Number(bookForm.totalCopies)
      });

      setBookMsg({ kind: 'success', text: 'Book added to the catalog.' });
      setBookForm({ title: '', author: '', isbn: '', totalCopies: '1' });
      loadBooks();
    } catch (error) {
      setBookMsg({ kind: 'error', text: error.message });
    }
  }

  async function handleBorrowSubmit(event) {
    event.preventDefault();
    setBorrowMsg({ kind: '', text: '' });

    if (!borrowForm.memberId || !borrowForm.bookId) {
      setBorrowMsg({ kind: 'error', text: 'Choose both a member and a book.' });
      return;
    }

    try {
      const borrowing = await api.post(`${CONFIG.borrowingService}/borrowings`, {
        memberId: Number(borrowForm.memberId),
        bookId: Number(borrowForm.bookId)
      });

      setBorrowMsg({
        kind: 'success',
        text: `Borrowed. Due back ${borrowing.dueDate} (borrowing #${borrowing.id}).`
      });
      setBorrowForm({ memberId: '', bookId: '' });
      loadBorrowView();
      loadReturns();
      loadDashboard();
    } catch (error) {
      setBorrowMsg({ kind: 'error', text: error.message });
    }
  }

  async function handleReturnBorrowing(borrowingId) {
    try {
      await api.put(`${CONFIG.borrowingService}/borrowings/${borrowingId}/return`);
      setReturnsMsg({ kind: 'success', text: 'Book returned successfully.' });
      loadReturns();
      loadDashboard();
      loadFines();
    } catch (error) {
      setReturnsMsg({ kind: 'error', text: error.message });
    }
  }

  async function handleFinesLookup(event) {
    event.preventDefault();
    const memberId = finesLookup.trim();
    loadFines(memberId);
  }

  async function handleNotificationsLookup(event) {
    event.preventDefault();
    const memberId = notificationsLookup.trim();
    loadNotifications(memberId);
  }

  const renderMemberTable = () => {
    if (loadingStates.members) {
      return <div className="loading-state">Loading…</div>;
    }

    if (!members.length) {
      return <div className="empty-state">No members yet. Add the first one using the form above.</div>;
    }

    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Email</th>
            <th>Phone</th>
            <th>Member since</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.id}>
              <td className="num">{member.id}</td>
              <td>{member.name}</td>
              <td>{member.email}</td>
              <td>{member.phone || '—'}</td>
              <td>{fmtDate(member.membershipDate)}</td>
              <td>{member.active ? tagFor('Active', 'available') : tagFor('Inactive', 'overdue')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderBooksTable = () => {
    if (loadingStates.books) {
      return <div className="loading-state">Loading…</div>;
    }

    if (!bookList.length) {
      return <div className="empty-state">No books in the catalog yet. Add one using the form above.</div>;
    }

    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Author</th>
            <th>ISBN</th>
            <th>Copies</th>
          </tr>
        </thead>
        <tbody>
          {bookList.map((book) => (
            <tr key={book.id}>
              <td className="num">{book.id}</td>
              <td>{book.title}</td>
              <td>{book.author}</td>
              <td>{book.isbn}</td>
              <td>
                <span className={`tag ${book.availableCopies > 0 ? 'tag-available' : 'tag-overdue'}`}>
                  {`${book.availableCopies} / ${book.totalCopies} available`}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderReturnsTable = () => {
    if (loadingStates.returns) {
      return <div className="loading-state">Loading…</div>;
    }

    if (!borrowings.length) {
      return <div className="empty-state">No active borrowings right now.</div>;
    }

    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Member</th>
            <th>Book</th>
            <th>Due date</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {borrowings.map((item) => (
            <tr key={item.id}>
              <td className="num">{item.id}</td>
              <td>{item.member?.name || item.memberId}</td>
              <td>{item.book?.title || item.bookId}</td>
              <td>{fmtDate(item.dueDate)}</td>
              <td>
                <button className="ghost" type="button" onClick={() => handleReturnBorrowing(item.id)}>
                  Mark returned
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderFinesTable = () => {
    if (loadingStates.fines) {
      return <div className="loading-state">Loading…</div>;
    }

    if (!fines.length) {
      return <div className="empty-state">No fines found.</div>;
    }

    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Member</th>
            <th>Amount</th>
            <th>Status</th>
            <th>Issued</th>
          </tr>
        </thead>
        <tbody>
          {fines.map((fine) => (
            <tr key={fine.id}>
              <td className="num">{fine.id}</td>
              <td>{fine.memberId}</td>
              <td>{fine.amount}</td>
              <td>{fine.status === 'UNPAID' ? tagFor('Unpaid', 'unpaid') : tagFor('Paid', 'paid')}</td>
              <td>{fmtDate(fine.generatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderNotificationsTable = () => {
    if (loadingStates.notifications) {
      return <div className="loading-state">Loading…</div>;
    }

    if (!notifications.length) {
      return <div className="empty-state">No notifications found.</div>;
    }

    return (
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Member</th>
            <th>Message</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {notifications.map((item) => (
            <tr key={item.id}>
              <td className="num">{item.id}</td>
              <td>{item.type || item.eventType || 'Notification'}</td>
              <td>{item.memberId}</td>
              <td>{item.message || item.content || '—'}</td>
              <td>{fmtDate(item.createdAt || item.sentAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  return (
    <div className="app">
      <aside className="rail">
        <div className="rail-brand">
          Smart Library
          <span>circulation desk</span>
        </div>
        <nav>
          {[
            'dashboard',
            'members',
            'books',
            'borrow',
            'returns',
            'fines',
            'notifications'
          ].map((view) => (
            <button
              key={view}
              type="button"
              className={`rail-item ${activeView === view ? 'active' : ''}`}
              onClick={() => setActiveView(view)}
            >
              {view === 'dashboard' && 'Dashboard'}
              {view === 'members' && 'Members'}
              {view === 'books' && 'Books'}
              {view === 'borrow' && 'Borrow a book'}
              {view === 'returns' && 'Returns'}
              {view === 'fines' && 'Fines'}
              {view === 'notifications' && 'Notifications'}
            </button>
          ))}
        </nav>
        <div className="rail-footer">Talks to 5 local services on ports 8081–8085.</div>
      </aside>

      <main className="main">
        {activeView === 'dashboard' && (
          <section className="view active">
            <div className="view-header">
              <h1>Dashboard</h1>
              <p>A quick read of where things stand across the system.</p>
            </div>

            <div className="stat-grid">
              {loadingStates.dashboard ? (
                <div className="loading-state">Loading…</div>
              ) : dashboardStats ? (
                <>
                  <div className="stat">
                    <span className="number">{dashboardStats.members}</span>
                    <span className="label">Members</span>
                  </div>
                  <div className="stat">
                    <span className="number">{dashboardStats.books}</span>
                    <span className="label">Book titles</span>
                  </div>
                  <div className="stat">
                    <span className="number">{dashboardStats.activeBorrowings}</span>
                    <span className="label">Books currently out</span>
                  </div>
                  <div className="stat">
                    <span className="number">{dashboardStats.unpaidFines}</span>
                    <span className="label">Unpaid fines</span>
                  </div>
                  <div className="stat">
                    <span className="number">{dashboardStats.unreadNotifications}</span>
                    <span className="label">Unread notifications</span>
                  </div>
                </>
              ) : null}
            </div>
          </section>
        )}

        {activeView === 'members' && (
          <section className="view active">
            <div className="view-header">
              <h1>Members</h1>
              <p>Library members on file. New members are created here and used everywhere else in the app.</p>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Add a member</h2>
              </div>
              <div className="panel-body">
                {memberMsg.text && <div className={`message show ${memberMsg.kind}`}>{memberMsg.text}</div>}
                <form onSubmit={handleMemberSubmit}>
                  <div className="form-row">
                    <div className="field">
                      <label htmlFor="member-name">Name</label>
                      <input
                        id="member-name"
                        type="text"
                        value={memberForm.name}
                        onChange={(e) => setMemberForm({ ...memberForm, name: e.target.value })}
                        required
                        maxLength={100}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="member-email">Email</label>
                      <input
                        id="member-email"
                        type="email"
                        value={memberForm.email}
                        onChange={(e) => setMemberForm({ ...memberForm, email: e.target.value })}
                        required
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="member-phone">Phone (10 digits)</label>
                      <input
                        id="member-phone"
                        type="text"
                        pattern="[0-9]{10}"
                        placeholder="9840012345"
                        value={memberForm.phone}
                        onChange={(e) => setMemberForm({ ...memberForm, phone: e.target.value })}
                      />
                    </div>
                    <button className="primary" type="submit">Add member</button>
                  </div>
                </form>
              </div>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>All members</h2>
              </div>
              <div className="panel-body">{renderMemberTable()}</div>
            </div>
          </section>
        )}

        {activeView === 'books' && (
          <section className="view active">
            <div className="view-header">
              <h1>Books</h1>
              <p>The catalog. Available copies update automatically as books are borrowed and returned.</p>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Add a book</h2>
              </div>
              <div className="panel-body">
                {bookMsg.text && <div className={`message show ${bookMsg.kind}`}>{bookMsg.text}</div>}
                <form onSubmit={handleBookSubmit}>
                  <div className="form-row">
                    <div className="field">
                      <label htmlFor="book-title">Title</label>
                      <input
                        id="book-title"
                        type="text"
                        value={bookForm.title}
                        onChange={(e) => setBookForm({ ...bookForm, title: e.target.value })}
                        required
                        maxLength={200}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="book-author">Author</label>
                      <input
                        id="book-author"
                        type="text"
                        value={bookForm.author}
                        onChange={(e) => setBookForm({ ...bookForm, author: e.target.value })}
                        required
                        maxLength={150}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="book-isbn">ISBN</label>
                      <input
                        id="book-isbn"
                        type="text"
                        value={bookForm.isbn}
                        onChange={(e) => setBookForm({ ...bookForm, isbn: e.target.value })}
                        required
                        maxLength={20}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="book-copies">Total copies</label>
                      <input
                        id="book-copies"
                        type="number"
                        min="1"
                        value={bookForm.totalCopies}
                        onChange={(e) => setBookForm({ ...bookForm, totalCopies: e.target.value })}
                        required
                      />
                    </div>
                    <button className="primary" type="submit">Add book</button>
                  </div>
                </form>
              </div>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Catalog</h2>
              </div>
              <div className="panel-body">{renderBooksTable()}</div>
            </div>
          </section>
        )}

        {activeView === 'borrow' && (
          <section className="view active">
            <div className="view-header">
              <h1>Borrow a book</h1>
              <p>Checks the member and the book's availability before creating the borrowing record.</p>
            </div>

            <div className="panel">
              <div className="panel-body">
                {borrowMsg.text && <div className={`message show ${borrowMsg.kind}`}>{borrowMsg.text}</div>}
                <form onSubmit={handleBorrowSubmit}>
                  <div className="form-row">
                    <div className="field">
                      <label htmlFor="borrow-member-select">Member</label>
                      <select
                        id="borrow-member-select"
                        value={borrowForm.memberId}
                        onChange={(e) => setBorrowForm({ ...borrowForm, memberId: e.target.value })}
                        required
                      >
                        <option value="">Select a member…</option>
                        {memberOptions.map((member) => (
                          <option key={member.id} value={member.id}>
                            {member.name} (#{member.id})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="field">
                      <label htmlFor="borrow-book-select">Book</label>
                      <select
                        id="borrow-book-select"
                        value={borrowForm.bookId}
                        onChange={(e) => setBorrowForm({ ...borrowForm, bookId: e.target.value })}
                        required
                      >
                        <option value="">Select a book…</option>
                        {bookOptions.map((book) => (
                          <option key={book.id} value={book.id}>
                            {book.title} ({book.availableCopies} available)
                          </option>
                        ))}
                      </select>
                    </div>

                    <button className="primary" type="submit">Borrow</button>
                  </div>
                </form>
                <p className="hint">Loan period and due date are set by Borrowing Service (default: 14 days).</p>
              </div>
            </div>
          </section>
        )}

        {activeView === 'returns' && (
          <section className="view active">
            <div className="view-header">
              <h1>Returns</h1>
              <p>Everything currently checked out. Mark a book returned to close out its borrowing record.</p>
            </div>
            <div className="panel">
              <div className="panel-body">
                {returnsMsg.text && <div className={`message show ${returnsMsg.kind}`}>{returnsMsg.text}</div>}
                {renderReturnsTable()}
              </div>
            </div>
          </section>
        )}

        {activeView === 'fines' && (
          <section className="view active">
            <div className="view-header">
              <h1>Fines</h1>
              <p>Overdue fines, generated automatically when a book is returned late or stays out past its due date.</p>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Look up by member</h2>
              </div>
              <div className="panel-body">
                <form onSubmit={handleFinesLookup}>
                  <div className="form-row">
                    <div className="field">
                      <label htmlFor="fines-lookup-member">Member ID</label>
                      <input
                        id="fines-lookup-member"
                        type="number"
                        min="1"
                        placeholder="Leave blank for all fines"
                        value={finesLookup}
                        onChange={(e) => setFinesLookup(e.target.value)}
                      />
                    </div>
                    <button className="ghost" type="submit">Look up</button>
                  </div>
                </form>
              </div>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Fines</h2>
              </div>
              <div className="panel-body">
                {finesMsg.text && <div className={`message show ${finesMsg.kind}`}>{finesMsg.text}</div>}
                {renderFinesTable()}
              </div>
            </div>
          </section>
        )}

        {activeView === 'notifications' && (
          <section className="view active">
            <div className="view-header">
              <h1>Notifications</h1>
              <p>Generated automatically for borrowing, returns, and fine activity.</p>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Look up by member</h2>
              </div>
              <div className="panel-body">
                <form onSubmit={handleNotificationsLookup}>
                  <div className="form-row">
                    <div className="field">
                      <label htmlFor="notifications-lookup-member">Member ID</label>
                      <input
                        id="notifications-lookup-member"
                        type="number"
                        min="1"
                        placeholder="Leave blank for all notifications"
                        value={notificationsLookup}
                        onChange={(e) => setNotificationsLookup(e.target.value)}
                      />
                    </div>
                    <button className="ghost" type="submit">Look up</button>
                  </div>
                </form>
              </div>
            </div>

            <div className="panel">
              <div className="panel-head">
                <h2>Recent activity</h2>
              </div>
              <div className="panel-body">{renderNotificationsTable()}</div>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}

export default App;
