
const { Pool } = require('pg');

const pool = new Pool({
  connectionString: 'postgresql://geotrackdb_user:WTrqAeeE6vJGwxlZnl1R7nGpycgdDELp@dpg-d6sgsjshg0os73f6s1jg-a.oregon-postgres.render.com/geotrackdb_a9cp',
  ssl: { rejectUnauthorized: false }
});

async function check() {
  try {
    const res = await pool.query("SELECT u.id, u.email, u.is_admin, u.is_super_admin, u.company_id, c.name as company_name FROM users u LEFT JOIN companies c ON u.company_id = c.id WHERE u.email = 'admin@test.com'");
    console.log(JSON.stringify(res.rows, null, 2));
    
    const resAllUsers = await pool.query("SELECT COUNT(*) FROM users");
    console.log("Total users in DB:", resAllUsers.rows[0].count);
    
    const resAllAgents = await pool.query("SELECT COUNT(*) FROM users WHERE is_admin = false");
    console.log("Total agents in DB (is_admin = false):", resAllAgents.rows[0].count);
    
    if (res.rows.length > 0) {
      const companyId = res.rows[0].company_id;
      const res2 = await pool.query("SELECT COUNT(*) FROM clients WHERE company_id = $1", [companyId]);
      console.log("Total clients for this company:", res2.rows[0].count);
      
      const resAgents = await pool.query("SELECT COUNT(*) FROM users WHERE company_id = $1 AND is_admin = false", [companyId]);
      console.log("Total agents for this company:", resAgents.rows[0].count);
    }
    
    const res3 = await pool.query("SELECT COUNT(*) FROM clients");
    console.log("Total clients in DB:", res3.rows[0].count);
    
  } catch (err) {
    console.error(err);
  } finally {
    await pool.end();
  }
}

check();
