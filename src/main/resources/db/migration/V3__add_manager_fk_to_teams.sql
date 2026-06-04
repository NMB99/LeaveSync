ALTER TABLE teams
    ADD CONSTRAINT fk_team_manager_id
    FOREIGN KEY (manager_id) REFERENCES users(id);