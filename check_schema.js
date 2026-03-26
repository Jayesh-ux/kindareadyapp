
const { Pool } = require('pg');
const pool = new Pool({
  connectionString: 'postgresql://geotrackdb_user:WTrqAeeE6vJGwxlZnl1R7nGpycgdDELp@dpg-d6sgsjshg0os73f6s1jg-a.oregon-postgres.render.com/geotrackdb_a9cp',
  ssl: { rejectUnauthorized: false }
});
async function check() {
  const rT = await pool.query("SELECT count(*) FROM clients");
  console.log("TOTAL CLIENTS IN DB:", rT.rows[0].count);
  
  const rG = await pool.query(`
    SELECT 
      company_id, 
      count(*) as total, 
      count(*) FILTER (WHERE latitude IS NOT NULL AND longitude IS NOT NULL) as with_location,
      count(*) FILTER (WHERE latitude IS NULL OR longitude IS NULL) as without_location
    FROM clients 
    GROUP BY company_id
  `);
  console.log("GEOCODING STATS BY COMPANY:");
  console.log(JSON.stringify(rG.rows, null, 2));

  await pool.end();
}
check();
